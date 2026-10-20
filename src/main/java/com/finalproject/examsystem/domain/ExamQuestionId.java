package com.finalproject.examsystem.domain;

import java.io.Serializable;

/**
 * exam_question 的复合主键：考试 × 作答轮次 × 题目。
 * <p>
 * 为什么必须包含 examId：
 * 早期版本用的是 (attemptNo, questionId)，看似够用，实则同一道题被两场考试引用时，
 * 两个 (1, 5) 会撞主键，组卷直接失败。加上 examId 后才是正确的语义：
 * 「某场考试的第 N 轮包含哪些题」。
 * <p>
 * 用 record 而非 Lombok 类：record 自动生成基于全部字段的 equals/hashCode（@IdClass 的硬性要求），
 * 且自带 Serializable。
 */
public record ExamQuestionId(Long examId, Integer attemptNo, Long questionId) implements Serializable {
}
