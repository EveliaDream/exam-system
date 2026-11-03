package com.finalproject.examsystem.service;

import com.finalproject.examsystem.domain.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客观题判分规则单元测试。
 * <p>
 * ObjectiveGrader 是无框架依赖的纯函数，所以这里直接测静态方法，不启动 Spring 上下文。
 * 判分规则是本项目最需要"边界清楚"的部分：一旦判错，学生成绩就错了，且很难事后解释。
 */
@DisplayName("客观题判分")
class ObjectiveGraderTest {

    // ==================== 单选 / 判断 ====================

    @Test
    @DisplayName("单选：答案一致得分")
    void singleCorrect() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.SINGLE, List.of("B"), List.of("B"))).isTrue();
    }

    @Test
    @DisplayName("单选：答案不一致不得分")
    void singleWrong() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.SINGLE, List.of("B"), List.of("C"))).isFalse();
    }

    @Test
    @DisplayName("单选：学生提交多项视为作答异常，不得分")
    void singleWithMultiplePicks() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.SINGLE, List.of("B"), List.of("B", "C"))).isFalse();
    }

    @Test
    @DisplayName("单选：空答案不得分")
    void singleBlank() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.SINGLE, List.of("B"), List.of())).isFalse();
        assertThat(ObjectiveGrader.isCorrect(QuestionType.SINGLE, List.of("B"), List.of("  "))).isFalse();
        assertThat(ObjectiveGrader.isCorrect(QuestionType.SINGLE, List.of("B"), null)).isFalse();
    }

    @Test
    @DisplayName("判断：T/F 判定正确")
    void judge() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.JUDGE, List.of("T"), List.of("T"))).isTrue();
        assertThat(ObjectiveGrader.isCorrect(QuestionType.JUDGE, List.of("T"), List.of("F"))).isFalse();
    }

    // ==================== 多选 ====================

    @Test
    @DisplayName("多选：集合完全一致才得分（顺序无关）")
    void multipleOrderInsensitive() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.MULTIPLE,
                List.of("A", "C"), List.of("C", "A"))).isTrue();
    }

    @Test
    @DisplayName("多选：漏选不得分")
    void multipleMissing() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.MULTIPLE,
                List.of("A", "B", "C"), List.of("A", "B"))).isFalse();
    }

    @Test
    @DisplayName("多选：多选不得分")
    void multipleExtra() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.MULTIPLE,
                List.of("A", "B"), List.of("A", "B", "C"))).isFalse();
    }

    @Test
    @DisplayName("多选：空答案不得分")
    void multipleBlank() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.MULTIPLE,
                List.of("A", "B"), List.of())).isFalse();
    }

    // ==================== 填空 ====================

    @Test
    @DisplayName("填空：忽略首尾空格、内部空白、大小写")
    void blankNormalized() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.BLANK,
                List.of("extends"), List.of("  Extends "))).isTrue();
        assertThat(ObjectiveGrader.isCorrect(QuestionType.BLANK,
                List.of("main"), List.of("m a i n"))).isTrue();
    }

    @Test
    @DisplayName("填空：全角字符按半角处理")
    void blankFullWidth() {
        // 全角字母 ｅｘｔｅｎｄｓ 应等价于 extends
        assertThat(ObjectiveGrader.isCorrect(QuestionType.BLANK,
                List.of("extends"), List.of("ｅｘｔｅｎｄｓ"))).isTrue();
    }

    @Test
    @DisplayName("填空：命中任一可接受答案即得分")
    void blankAnyAcceptable() {
        List<String> acceptable = List.of("main", "main方法", "main()");
        assertThat(ObjectiveGrader.isCorrect(QuestionType.BLANK, acceptable, List.of("MAIN方法"))).isTrue();
        assertThat(ObjectiveGrader.isCorrect(QuestionType.BLANK, acceptable, List.of("main()"))).isTrue();
        assertThat(ObjectiveGrader.isCorrect(QuestionType.BLANK, acceptable, List.of("run"))).isFalse();
    }

    @Test
    @DisplayName("填空：空答案不得分")
    void blankEmpty() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.BLANK, List.of("extends"), List.of(""))).isFalse();
        assertThat(ObjectiveGrader.isCorrect(QuestionType.BLANK, List.of("extends"), List.of("   "))).isFalse();
    }

    // ==================== 标准化 ====================

    @Test
    @DisplayName("标准化：全角转半角并去除所有空白")
    void normalize() {
        assertThat(ObjectiveGrader.normalize("　Ａ Ｂ　Ｃ ")).isEqualTo("abc");
        assertThat(ObjectiveGrader.normalize(null)).isEmpty();
        assertThat(ObjectiveGrader.normalize("   ")).isEmpty();
    }

    @Test
    @DisplayName("标准答案为空时一律判错（防御脏数据）")
    void emptyCorrectAnswer() {
        assertThat(ObjectiveGrader.isCorrect(QuestionType.SINGLE, List.of(), List.of("A"))).isFalse();
        assertThat(ObjectiveGrader.isCorrect(QuestionType.SINGLE, null, List.of("A"))).isFalse();
    }
}
