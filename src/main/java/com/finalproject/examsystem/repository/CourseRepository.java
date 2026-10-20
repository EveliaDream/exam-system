package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByDeletedFalseOrderByIdAsc();

    Optional<Course> findByIdAndDeletedFalse(Long id);
}
