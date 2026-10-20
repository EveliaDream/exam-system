package com.finalproject.examsystem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 班级。 */
@Entity
@Table(name = "student_class")
@Getter
@Setter
@NoArgsConstructor
public class StudentClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** 年级，如 2023 */
    @Column(length = 20)
    private String grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean deleted = false;

    public static StudentClass create(String name, String grade, User teacher) {
        StudentClass clazz = new StudentClass();
        clazz.setName(name);
        clazz.setGrade(grade);
        clazz.setTeacher(teacher);
        return clazz;
    }
}
