package com.finalproject.examsystem.dto.exam;

import com.finalproject.examsystem.domain.Difficulty;
import com.finalproject.examsystem.domain.QuestionType;

import java.util.List;

/** 组卷中的一道题（快照视图，供教师预览）。 */
public record ExamQuestionView(Long questionId,
                               QuestionType type,
                               String typeLabel,
                               String stem,
                               List<String> options,
                               List<String> answer,
                               Integer score,
                               String analysis,
                               Integer sortOrder,
                               /** 该题在题库中的知识点，便于教师检查覆盖是否均衡 */
                               List<Long> knowledgePointIds,
                               List<String> knowledgePointNames) {
}
