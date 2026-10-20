package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.ExamQuestion;
import com.finalproject.examsystem.domain.ExamQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, ExamQuestionId> {

    List<ExamQuestion> findByExamIdAndAttemptNoOrderBySortOrderAsc(Long examId, Integer attemptNo);

    long countByExamIdAndAttemptNo(Long examId, Integer attemptNo);

    void deleteByExamIdAndAttemptNo(Long examId, Integer attemptNo);

    /** 删除整场考试的所有轮次快照（删除草稿时使用） */
    void deleteByExamId(Long examId);

    /** 某道题是否已被任何考试引用：题目删除前的保护检查 */
    long countByQuestionId(Long questionId);
}
