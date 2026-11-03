package com.finalproject.examsystem.dto.answer;

import com.finalproject.examsystem.domain.ExamQuestion;
import com.finalproject.examsystem.domain.QuestionType;

import java.util.List;

/**
 * 学生答题页看到的题目。
 * <p>
 * <b>刻意不包含 answer 与 analysis 字段</b>：学生端接口一旦带上正确答案，
 * 打开 F12 就能看到。所以这里用独立 DTO 而不是直接返回 ExamQuestion 实体。
 * 交卷后才通过结果接口展示正确答案与解析。
 */
public record PaperQuestionResponse(Long questionId,
                                    QuestionType type,
                                    String typeLabel,
                                    String stem,
                                    List<String> options,
                                    Integer score,
                                    Integer sortOrder) {

    public static PaperQuestionResponse from(ExamQuestion eq) {
        return new PaperQuestionResponse(
                eq.getQuestionId(),
                eq.getType(),
                eq.getType() == null ? null : eq.getType().getLabel(),
                eq.getStem(),
                eq.getOptions() == null ? List.of() : eq.getOptions(),
                eq.getScore(),
                eq.getSortOrder());
    }
}
