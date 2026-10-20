package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.ExamQuestion;
import com.finalproject.examsystem.domain.ExamQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, ExamQuestionId> {

    List<ExamQuestion> findByExamIdAndAttemptNoOrderBySortOrderAsc(Long examId, Integer attemptNo);

    long countByExamIdAndAttemptNo(Long examId, Integer attemptNo);

    void deleteByExamIdAndAttemptNo(Long examId, Integer attemptNo);
}
