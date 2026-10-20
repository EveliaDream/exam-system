package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.ExamClass;
import com.finalproject.examsystem.domain.ExamClassId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamClassRepository extends JpaRepository<ExamClass, ExamClassId> {

    List<ExamClass> findByExamId(Long examId);

    List<ExamClass> findByClassId(Long classId);

    void deleteByExamId(Long examId);

    void deleteByExamIdAndClassId(Long examId, Long classId);
}
