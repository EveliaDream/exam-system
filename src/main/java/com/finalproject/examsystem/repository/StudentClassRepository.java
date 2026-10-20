package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.StudentClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {

    List<StudentClass> findByDeletedFalseOrderByIdAsc();

    Optional<StudentClass> findByIdAndDeletedFalse(Long id);

    boolean existsByNameAndDeletedFalse(String name);

    /** 修改班级时用来排除自身，避免"自己和自己重名" */
    boolean existsByNameAndDeletedFalseAndIdNot(String name, Long id);

    /** 当前教师名下的班级（用于下拉选择与数据隔离校验） */
    List<StudentClass> findByTeacherIdAndDeletedFalseOrderByIdAsc(Long teacherId);

    @Query("""
            select c from com.finalproject.examsystem.domain.StudentClass c
            where c.deleted = false
              and (:keyword is null or c.name like %:keyword%)
            """)
    Page<StudentClass> search(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 按教师数据范围分页查询班级。
     * <p>
     * 隔离条件写在查询里，保证 total 与实际可见数据一致（否则分页总数会包含其他教师的班级）。
     */
    @Query("""
            select c from com.finalproject.examsystem.domain.StudentClass c
            where c.deleted = false
              and (:teacherId is null or c.teacher.id = :teacherId)
              and (:keyword is null or c.name like %:keyword%)
            """)
    Page<StudentClass> searchByTeacherScope(@Param("teacherId") Long teacherId,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);
}
