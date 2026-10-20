package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.Exam;
import com.finalproject.examsystem.domain.ExamStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    Optional<Exam> findByIdAndDeletedFalse(Long id);

    List<Exam> findByCreatedByIdAndDeletedFalseOrderByIdDesc(Long teacherId);

    @Query("""
            select e from com.finalproject.examsystem.domain.Exam e
            where e.deleted = false
              and (:status is null or e.status = :status)
              and (:createdBy is null or e.createdBy.id = :createdBy)
              and (:keyword is null or e.title like %:keyword%)
            """)
    Page<Exam> search(@Param("status") ExamStatus status,
                      @Param("createdBy") Long createdBy,
                      @Param("keyword") String keyword,
                      Pageable pageable);
}
