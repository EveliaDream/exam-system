package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByRecordId(Long recordId);

    Optional<Answer> findByRecordIdAndQuestionId(Long recordId, Long questionId);

    long countByRecordId(Long recordId);

    /** 判分后统计答对题数，用于成绩摘要 */
    long countByRecordIdAndCorrectTrue(Long recordId);

    void deleteByRecordId(Long recordId);
}
