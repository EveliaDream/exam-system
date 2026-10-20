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

/**
 * 知识点。
 * <p>
 * 学情分析的主干：系统通过统计学生「答对/答错题目所覆盖的知识点」来判断其掌握程度。
 * 因此知识点必须是可枚举、可关联、可统计的一等实体，而不是题干上的自由文本标签。
 * <p>
 * courseId 为空表示通用知识点（如公共课的基础语法），不绑定具体课程。
 * 不直接引用 Course 实体，是为了让知识点管理不依赖课程模块的完成度。
 */
@Entity
@Table(name = "knowledge_point")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "course_id")
    private Long courseId;

    /** 同一课程内的展示顺序，便于按教学章节排列 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean deleted = false;

    public static KnowledgePoint create(String name, String description, Long courseId, int sortOrder) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName(name);
        kp.setDescription(description);
        kp.setCourseId(courseId);
        kp.setSortOrder(sortOrder);
        return kp;
    }
}
