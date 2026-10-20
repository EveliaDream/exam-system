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

/**
 * 一次作答记录（学生 × 考试 × 轮次）。
 * <p>
 * 生成时机：学生点击"开始考试"时才创建并预填 answer 行，
 * 而不是发布考试时给全班生成，避免数据量无谓膨胀。
 */
@Entity
@Table(name = "exam_record")
@Getter
@Setter
@NoArgsConstructor
public class ExamRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    /** 开始作答时间，用于服务端计算截止时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "submit_time")
    private LocalDateTime submitTime;

    /** 客观题得分（本系统无主观题，即最终成绩） */
    @Column(name = "objective_score")
    private Integer objectiveScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.NOT_STARTED;

    /**
     * 切屏/失焦次数。
     * <p>
     * 只做服务端可验证的记录，不做摄像头人脸识别那类方案：
     * 前端上报 + 服务端留痕，供教师参考，不自动判作弊。
     */
    @Column(name = "switch_screen_count", nullable = false)
    private Integer switchScreenCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 是否已结束作答 */
    public boolean finished() {
        return status != null && status.isFinished();
    }

    /** 本次作答的截止时间；未开始时返回 null */
    public LocalDateTime deadline(Exam exam) {
        return startTime == null ? null : exam.deadlineOf(startTime);
    }
}
