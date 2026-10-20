package com.finalproject.examsystem.domain;

import com.finalproject.examsystem.domain.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;

/**
 * 题目。四种题型共用一张表。
 * <p>
 * 数据约定：
 * <ul>
 *   <li>SINGLE / MULTIPLE：options = ["选项A内容","选项B内容",...]，answer = ["A"] / ["A","C"]</li>
 *   <li>JUDGE：options = ["正确","错误"]，answer = ["T"] 或 ["F"]</li>
 *   <li>BLANK：options 为空，answer = ["标准答案","可接受的其他写法"]，命中任一即得分</li>
 * </ul>
 */
@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    /** 题干（支持纯文本，图片题干后续用 image_url 扩展） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> options;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> answer;

    /** 建议分值，组卷时可覆盖（exam_question.score 才是该场考试的最终分值） */
    @Column(nullable = false)
    private Integer score = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty = Difficulty.MEDIUM;

    /** 解析，学生查看答卷时展示 */
    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean deleted = false;

    /** 该题的正确答案，统一为字符串列表，便于作答与判分复用同一结构 */
    public List<String> correctAnswer() {
        return answer == null ? List.of() : answer;
    }
}
