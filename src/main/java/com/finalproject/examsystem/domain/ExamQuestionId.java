package com.finalproject.examsystem.domain;

import java.io.Serializable;

/**
 * exam_question 的复合主键。
 * <p>
 * 用 record 而非 Lombok 类：record 自动生成基于全部字段的 equals/hashCode（@IdClass 的硬性要求），
 * 且自带 Serializable，代码更少更不容易出错。
 * <p>
 * 组合方式（attemptNo + questionId）的含义：按"作答轮次 + 题号"存快照，
 * 这样同一场考试允许补考时重新组卷，也不会因为题库被修改而影响历史成绩。
 */
public record ExamQuestionId(Integer attemptNo, Long questionId) implements Serializable {
}
