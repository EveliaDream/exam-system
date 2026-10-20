package com.finalproject.examsystem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 考试（试卷定义）。 */
@Entity
@Table(name = "exam")
@Getter
@Setter
@NoArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    /** 试卷总分，发布前必须等于 exam_question 的分值之和 */
    @Column(name = "total_score", nullable = false)
    private Integer totalScore = 100;

    @Column(name = "pass_score", nullable = false)
    private Integer passScore = 60;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** 作答时长（分钟）。实际截止 = min(开始作答 + duration, end_time)，由服务端计算 */
    @Column(nullable = false)
    private Integer duration = 60;

    /** 题目顺序是否随机 */
    @Column(name = "shuffle_question", nullable = false)
    private Boolean shuffleQuestion = false;

    /** 选项顺序是否随机 */
    @Column(name = "shuffle_option", nullable = false)
    private Boolean shuffleOption = false;

    /** 允许的最大作答次数 */
    @Column(name = "max_attempt", nullable = false)
    private Integer maxAttempt = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status = ExamStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean deleted = false;

    /**
     * 单次作答的截止时间。
     * <p>
     * 权威倒计时只在这里算：startTimeOfAttempt + duration 与 endTime 取较小值。
     * 前端倒计时仅用于展示，服务端每次提交都必须重新校验。
     */
    public LocalDateTime deadlineOf(LocalDateTime attemptStart) {
        LocalDateTime byDuration = attemptStart.plusMinutes(duration);
        return byDuration.isBefore(endTime) ? byDuration : endTime;
    }
}
