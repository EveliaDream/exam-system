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

    /**
     * 随机组卷的候选 id 列表。
     * <p>
     * 只取 id 再在内存里洗牌，而不是写 {@code order by rand()}：
     * 后者在 MySQL 上会全表排序、在 H2 上根本不可移植，而课程级题库只有几百道题，
     * 取 id 后内存随机既快又可移植。
     */
    @Query("""
            select q.id from com.finalproject.examsystem.domain.Question q
            where q.deleted = false
              and q.enabled = true
              and (:courseId is null or q.course.id = :courseId)
              and (:type is null or q.type = :type)
              and (:difficulty is null or q.difficulty = :difficulty)
            """)
    List<Long> findIdsForAssembly(@Param("courseId") Long courseId,
                                  @Param("type") QuestionType type,
                                  @Param("difficulty") Difficulty difficulty);

    /** 批量加载实体（组卷时用，保持与 id 列表的对应由调用方处理） */
    List<Question> findByIdInAndDeletedFalse(List<Long> ids);
}
