package com.finalproject.examsystem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 题目与知识点的多对多关联。
 * <p>
 * 用独立的关联实体而不是 {@code @ManyToMany}：关联表要参与统计查询
 * （按知识点聚合正确率），独立实体更容易写 JPQL 与维护索引。
 * <p>
 * 考题通常具有综合性，一道题挂 2~3 个知识点是正常的，不能压缩成题目表上的单个字段。
 */
@Entity
@Table(name = "question_knowledge")
@IdClass(QuestionKnowledgeId.class)
@Getter
@Setter
@NoArgsConstructor
public class QuestionKnowledge {

    @Id
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Id
    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    /**
     * 只读关联，仅用于查询时 join 取知识点名称（insertable/updatable = false）。
     * <p>
     * 主键仍由上面的两个字段承载，因此这里不改变复合主键的语义。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_point_id", insertable = false, updatable = false)
    private KnowledgePoint knowledgePoint;

    public static QuestionKnowledge of(Long questionId, Long knowledgePointId) {
        QuestionKnowledge qk = new QuestionKnowledge();
        qk.setQuestionId(questionId);
        qk.setKnowledgePointId(knowledgePointId);
        return qk;
    }
}
