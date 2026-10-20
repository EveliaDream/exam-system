package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.QuestionKnowledge;
import com.finalproject.examsystem.domain.QuestionKnowledgeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionKnowledgeRepository extends JpaRepository<QuestionKnowledge, QuestionKnowledgeId> {

    List<QuestionKnowledge> findByQuestionId(Long questionId);

    List<QuestionKnowledge> findByQuestionIdIn(List<Long> questionIds);

    void deleteByQuestionId(Long questionId);

    long countByKnowledgePointId(Long knowledgePointId);

    /**
     * 按题目批量取「题目 → 知识点」，供题目列表展示标签。
     * <p>
     * 一次取回，避免列表页对每行都查一次知识点（N+1）。返回 Object[]：[0] questionId, [1] 知识点实体。
     */
    @Query("""
            select qk.questionId, kp
            from com.finalproject.examsystem.domain.QuestionKnowledge qk
            join qk.knowledgePoint kp
            where qk.questionId in :questionIds
              and kp.deleted = false
            order by qk.questionId, kp.sortOrder
            """)
    List<Object[]> findKnowledgeByQuestionIds(@Param("questionIds") List<Long> questionIds);
}
