package com.finalproject.examsystem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 考试发布范围：exam × class。学生"我的考试"列表按此过滤。 */
@Entity
@Table(name = "exam_class")
@IdClass(ExamClassId.class)
@Getter
@Setter
@NoArgsConstructor
public class ExamClass {

    @Id
    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Id
    @Column(name = "class_id", nullable = false)
    private Long classId;

    public static ExamClass of(Long examId, Long classId) {
        ExamClass ec = new ExamClass();
        ec.setExamId(examId);
        ec.setClassId(classId);
        return ec;
    }
}
