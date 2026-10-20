package com.finalproject.examsystem.domain;

import com.finalproject.examsystem.domain.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 组卷快照：考试包含哪些题、每题多少分、题面与答案的当时版本。
 * <p>
 * 关键设计：answer 与 stem 在这里冗余一份快照。
 * 否则教师后来修改题库，会导致已考完的考试成绩发生变化——这是本项目最容易被抓的逻辑漏洞。
 */
@Entity
@Table(name = "exam_question")
@IdClass(ExamQuestionId.class)
@Getter
@Setter
@NoArgsConstructor
public class ExamQuestion {

    /** 作答轮次，默认 1（补考时为 2、3……） */
    @Id
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    @Id
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** 归属考试。仅作关联，不参与主键，避免同一题在不同考试间冲突 */
    @Column(name = "exam_id", nullable = false)
    private Long examId;

    // ===== 以下均为组卷时的快照 =====

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private QuestionType type;

    @Column(name = "stem", nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Convert(converter = StringListConverter.class)
    @Column(name = "options", columnDefinition = "TEXT")
    private List<String> options;

    /** 正确答案快照，判分只读这里，绝不回查题库 */
    @Convert(converter = StringListConverter.class)
    @Column(name = "answer", columnDefinition = "TEXT")
    private List<String> answer;

    @Column(name = "analysis", columnDefinition = "TEXT")
    private String analysis;

    /** 该题在本场考试中的最终分值 */
    @Column(name = "score", nullable = false)
    private Integer score = 5;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 从题库复制一份快照 */
    public static ExamQuestion snapshot(Exam exam, Question question, int score, int sortOrder) {
        ExamQuestion eq = new ExamQuestion();
        eq.setAttemptNo(1);
        eq.setQuestionId(question.getId());
        eq.setExamId(exam.getId());
        eq.setType(question.getType());
        eq.setStem(question.getStem());
        eq.setOptions(question.getOptions() == null ? List.of() : List.copyOf(question.getOptions()));
        eq.setAnswer(question.correctAnswer() == null ? List.of() : List.copyOf(question.correctAnswer()));
        eq.setAnalysis(question.getAnalysis());
        eq.setScore(score);
        eq.setSortOrder(sortOrder);
        return eq;
    }
}
