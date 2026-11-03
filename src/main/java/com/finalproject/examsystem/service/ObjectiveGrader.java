package com.finalproject.examsystem.service;

import com.finalproject.examsystem.domain.QuestionType;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 客观题判分。
 * <p>
 * 刻意做成**无框架依赖的纯函数**：不给它注入任何 Repository 或 Spring 组件，
 * 这样判分规则可以用单元测试逐条验证，也便于日后替换实现。
 * <p>
 * 输入只有「题型 + 标准答案 + 学生答案」，答案来自 {@code exam_question} 快照，
 * 绝不回查题库 —— 这是「组卷快照」设计的直接收益：教师后来改题不会影响历史判分。
 */
public final class ObjectiveGrader {

    private ObjectiveGrader() {
    }

    /**
     * 判定一道题是否得分。
     * <p>
     * 规则（不同题型差异较大，逐条说明）：
     * <ul>
     *   <li><b>单选 / 判断</b>：学生答案必须恰好一项，且与标准答案一致（忽略大小写）</li>
     *   <li><b>多选</b>：答案集合完全一致才得分。漏选、多选都不得分——
     *       这比「漏选得一半」更好解释，也更容易向学生说明</li>
     *   <li><b>填空</b>：答案标准化（去空白、忽略大小写、全角转半角）后，
     *       命中任一可接受答案即得分</li>
     * </ul>
     * 空答案一律判错。
     */
    public static boolean isCorrect(QuestionType type, List<String> correctAnswer, List<String> studentAnswer) {
        if (type == null || correctAnswer == null || correctAnswer.isEmpty()) {
            return false;
        }
        if (studentAnswer == null || studentAnswer.stream().allMatch(ObjectiveGrader::isBlank)) {
            return false;
        }

        return switch (type) {
            case SINGLE, JUDGE -> matchesSingle(correctAnswer, studentAnswer);
            case MULTIPLE -> matchesMultiple(correctAnswer, studentAnswer);
            case BLANK -> matchesBlank(correctAnswer, studentAnswer);
        };
    }

    private static boolean matchesSingle(List<String> correct, List<String> student) {
        List<String> picked = student.stream()
                .filter(s -> !isBlank(s))
                .map(s -> s.trim())
                .toList();
        if (picked.size() != 1) {
            return false;
        }
        return correct.stream().anyMatch(c -> Objects.equals(c.trim(), picked.get(0)));
    }

    private static boolean matchesMultiple(List<String> correct, List<String> student) {
        // 多选：集合完全等价。用 TreeSet 归一化，避免顺序与重复带来的误判
        Set<String> expected = correct.stream().map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actual = student.stream()
                .filter(s -> !isBlank(s))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        return !actual.isEmpty() && expected.equals(actual);
    }

    private static boolean matchesBlank(List<String> correct, List<String> student) {
        // 填空题只接受一项（多空填空题留作扩展），但允许该空有多个可接受写法
        String actual = student.stream()
                .filter(s -> !isBlank(s))
                .map(ObjectiveGrader::normalize)
                .findFirst()
                .orElse("");
        if (actual.isEmpty()) {
            return false;
        }
        return correct.stream().map(ObjectiveGrader::normalize).anyMatch(actual::equals);
    }

    /**
     * 填空题答案标准化。
     * <p>
     * 依次做四件事：全角转半角 → 去掉所有空白字符 → 转小写 → 去首尾空白。
     * 目的是让「extends」「Extends 」「ｅｘｔｅｎｄｓ」被视为同一个答案，
     * 覆盖同一答案的不同书写方式。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if (c >= '\uFF01' && c <= '\uFF5E') {
                // 全角 ASCII 区 → 半角
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\u3000') {
                // 全角空格统一成普通空格，后面再被去掉
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString()
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
