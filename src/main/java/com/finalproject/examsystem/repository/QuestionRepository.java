package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.Difficulty;
import com.finalproject.examsystem.domain.Question;
import com.finalproject.examsystem.domain.QuestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findByIdAndDeletedFalse(Long id);

    @Query("""
            select q from com.finalproject.examsystem.domain.Question q
            where q.deleted = false
              and (:courseId is null or q.course.id = :courseId)
              and (:type is null or q.type = :type)
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:enabled is null or q.enabled = :enabled)
              and (:keyword is null or q.stem like %:keyword%)
              and (:knowledgePointId is null or exists (
                    select 1 from com.finalproject.examsystem.domain.QuestionKnowledge qk
                    where qk.questionId = q.id and qk.knowledgePointId = :knowledgePointId))
            """)
    Page<Question> search(@Param("courseId") Long courseId,
                          @Param("type") QuestionType type,
                          @Param("difficulty") Difficulty difficulty,
                          @Param("enabled") Boolean enabled,
                          @Param("keyword") String keyword,
                          @Param("knowledgePointId") Long knowledgePointId,
                          Pageable pageable);

    /** 按条件抽题（组卷用），随机排序由 Pageable 的 Sort 或后续 SQL 决定 */
    List<Question> findByTypeAndDifficultyAndDeletedFalseAndEnabledTrue(QuestionType type, Difficulty difficulty);

    long countByCourseIdAndDeletedFalse(Long courseId);
}
