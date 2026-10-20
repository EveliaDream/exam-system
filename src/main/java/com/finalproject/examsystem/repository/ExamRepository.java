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

    /**
     * 学生端「我的考试」：只返回发布给该学生所在班级、且已发布的考试。
     * <p>
     * 用 exists 子查询而不是 join，避免多班级绑定时产生重复行。
     * 已结束（FINISHED）的考试也返回，学生需要能查历史。
     */
    @Query("""
            select e from com.finalproject.examsystem.domain.Exam e
            where e.deleted = false
              and e.status in (com.finalproject.examsystem.domain.ExamStatus.PUBLISHED,
                               com.finalproject.examsystem.domain.ExamStatus.FINISHED)
              and exists (
                    select 1 from com.finalproject.examsystem.domain.ExamClass ec
                    where ec.examId = e.id and ec.classId = :classId)
            order by e.startTime desc
            """)
    List<Exam> findVisibleToClass(@Param("classId") Long classId);
}
