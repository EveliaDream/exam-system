package com.finalproject.examsystem.dto.analytics;

import java.util.List;

/**
 * 学生端逐题作答详情。
 * <p>
 * 交卷后才开放：这里才展示正确答案与解析。
 * 作答过程中的题面接口（PaperQuestionResponse）永远不带这些字段。
 */
public record RecordDetailResponse(
        Long recordId,
        Long examId,
        String examTitle,
        Integer score,
        Integer totalScore,
        Integer passScore,
        Boolean passed,
        List<AnsweredQuestion> questions) {

    public record AnsweredQuestion(
            Long questionId,
            int order,
            String typeLabel,
            String stem,
            List<String> options,
            /** 标准答案（交卷后可见） */
            List<String> correctAnswer,
            /** 学生答案 */
            List<String> studentAnswer,
            Integer score,
            Integer maxScore,
            Boolean correct,
            Integer timeSpentSec,
            Integer submitCount,
            String analysis) {
    }
}
