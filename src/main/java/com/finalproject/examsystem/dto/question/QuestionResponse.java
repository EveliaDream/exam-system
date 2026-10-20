package com.finalproject.examsystem.dto.question;

import com.finalproject.examsystem.domain.Difficulty;
import com.finalproject.examsystem.domain.Question;
import com.finalproject.examsystem.domain.QuestionType;

import java.util.List;

/**
 * 题目响应。
 * <p>
 * 额外返回 knowledgePoints（知识点标签）：教师录题与组卷时需要看到该题属于哪些知识点，
 * 也是后续按知识点统计掌握度的依据。
 */
public record QuestionResponse(Long id,
                               QuestionType type,
                               String typeLabel,
                               String stem,
                               List<String> options,
                               List<String> answer,
                               Integer score,
                               Difficulty difficulty,
                               String difficultyLabel,
                               String analysis,
                               Long courseId,
                               String courseName,
                               Boolean enabled,
                               List<KnowledgeTag> knowledgePoints) {

    /** 知识点标签：只暴露 id 与名称，避免前端拿到整棵知识点对象 */
    public record KnowledgeTag(Long id, String name) {
    }

    public static QuestionResponse from(Question q, List<KnowledgeTag> tags) {
        return new QuestionResponse(
                q.getId(),
                q.getType(),
                q.getType() == null ? null : q.getType().getLabel(),
                q.getStem(),
                q.getOptions() == null ? List.of() : q.getOptions(),
                q.getAnswer() == null ? List.of() : q.getAnswer(),
                q.getScore(),
                q.getDifficulty(),
                q.getDifficulty() == null ? null : q.getDifficulty().getLabel(),
                q.getAnalysis(),
                q.getCourse() == null ? null : q.getCourse().getId(),
                q.getCourse() == null ? null : q.getCourse().getName(),
                q.getEnabled(),
                tags == null ? List.of() : tags);
    }
}
