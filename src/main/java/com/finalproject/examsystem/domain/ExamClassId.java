package com.finalproject.examsystem.domain;

import java.io.Serializable;

/** exam_class 的复合主键：一场考试发布给哪些班级。 */
public record ExamClassId(Long examId, Long classId) implements Serializable {
}
