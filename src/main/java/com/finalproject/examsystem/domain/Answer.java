package com.finalproject.examsystem.domain;

import com.finalproject.examsystem.domain.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生作答明细（一道题一行）。
 * <p>
 * 进入考试时按 exam_question 预填所有行（学生答案为空），
 * 自动保存时 UPDATE 对应行，判分时只更新 score / correct。
 * 这样断线重进可以直接恢复已答内容，无需额外缓存中间件。
 */
@Entity
@Table(name = "answer",
        uniqueConstraints = @UniqueConstraint(name = "uk_answer_record_question",
                columnNames = {"record_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private ExamRecord record;

    /** 与 exam_question 快照的 question_id 对应 */
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** 学生答案：SINGLE/JUDGE 单元素，MULTIPLE 多元素，BLANK 单元素 */
    @Convert(converter = StringListConverter.class)
    @Column(name = "student_answer", columnDefinition = "TEXT")
    private List<String> studentAnswer;

    @Column(name = "score")
    private Integer score;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt = LocalDateTime.now();

    /**
     * 首次作答时间。
     * <p>
     * 与 answeredAt 的差值可复核作答耗时；timeSpentSec 由前端上报，
     * 保存首次时间是为了在客户端数据缺失时仍能兜底估算。
     */
    @Column(name = "first_answered_at")
    private LocalDateTime firstAnsweredAt;

    /**
     * 该题累计作答耗时（秒），由前端用时间戳差值计算后上报。
     * <p>
     * 注意：不要让前端用 setInterval 累加计时——浏览器会把后台标签页的定时器降频，
     * 导致耗时严重偏小；必须用「离开该题/提交时的时间戳 − 进入该题的时间戳」。
     * 该字段用于「难度 × 耗时」交叉分析，判断题目是真难还是表述不清。
     */
    @Column(name = "time_spent_sec")
    private Integer timeSpentSec;

    /** 该题的提交/修改次数，反映学生在该题上的犹豫程度，用于辅助判断掌握牢固度 */
    @Column(name = "submit_count", nullable = false)
    private Integer submitCount = 0;

    public static Answer empty(ExamRecord record, Long questionId) {
        Answer answer = new Answer();
        answer.setRecord(record);
        answer.setQuestionId(questionId);
        answer.setStudentAnswer(List.of());
        return answer;
    }

    /** 是否已作答（有非空答案） */
    public boolean answered() {
        return studentAnswer != null && studentAnswer.stream().anyMatch(s -> s != null && !s.isBlank());
    }
}
