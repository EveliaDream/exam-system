package com.finalproject.examsystem.domain;

import java.io.Serializable;

/** question_knowledge 的复合主键（题目 × 知识点）。 */
public record QuestionKnowledgeId(Long questionId, Long knowledgePointId) implements Serializable {
}
