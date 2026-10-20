package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.ExamRecord;
import com.finalproject.examsystem.domain.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRecordRepository extends JpaRepository<ExamRecord, Long> {

    List<ExamRecord> findByExamIdAndStudentId(Long examId, Long studentId);

    Optional<ExamRecord> findByExamIdAndStudentIdAndAttemptNo(Long examId, Long studentId, Integer attemptNo);

    List<ExamRecord> findByExamIdOrderByObjectiveScoreDesc(Long examId);

    List<ExamRecord> findByStudentIdOrderByIdDesc(Long studentId);

    long countByExamIdAndStatus(Long examId, RecordStatus status);

    /**
     * 判断学生是否已有作答记录。
     * <p>
     * 有作答记录的账号不允许物理删除，只能停用：否则成绩单里会出现指向不存在学生的孤儿记录，
     * 且成绩争议时无法追溯。
     */
    long countByStudentId(Long studentId);
}
