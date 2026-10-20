package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.KnowledgePoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    Optional<KnowledgePoint> findByIdAndDeletedFalse(Long id);

    List<KnowledgePoint> findByDeletedFalseOrderBySortOrderAscIdAsc();

    List<KnowledgePoint> findByCourseIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long courseId);

    /** 名称在同一课程内不允许重复；课程为空时按"通用知识点"判重 */
    @Query("""
            select count(k) > 0 from com.finalproject.examsystem.domain.KnowledgePoint k
            where k.deleted = false
              and k.name = :name
              and ((:courseId is null and k.courseId is null) or k.courseId = :courseId)
              and (:excludeId is null or k.id <> :excludeId)
            """)
    boolean existsSameName(@Param("name") String name,
                           @Param("courseId") Long courseId,
                           @Param("excludeId") Long excludeId);

    @Query("""
            select k from com.finalproject.examsystem.domain.KnowledgePoint k
            where k.deleted = false
              and (:courseId is null or k.courseId is null or k.courseId = :courseId)
              and (:keyword is null or k.name like %:keyword%)
            """)
    Page<KnowledgePoint> search(@Param("courseId") Long courseId,
                                @Param("keyword") String keyword,
                                Pageable pageable);
}
