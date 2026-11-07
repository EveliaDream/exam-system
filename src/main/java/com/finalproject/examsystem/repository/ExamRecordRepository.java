package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.ExamRecord;
import com.finalproject.examsystem.domain.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRecordRepository extends JpaRepository<ExamRecord, Long> {

    List<ExamRecord> findByExamIdAndStudentId(Long examId, Long studentId);

    Optional<ExamRecord> findByExamIdAndStudentIdAndAttemptNo(Long examId, Long studentId, Integer attemptNo);

    /** 取该学生在这场考试中最新一轮的记录：用于「继续作答」与限考次数判断 */
    Optional<ExamRecord> findTopByExamIdAndStudentIdOrderByAttemptNoDesc(Long examId, Long studentId);

    /** 按 id 与学员双重定位：防止学生改 recordId 访问别人的作答记录 */
    Optional<ExamRecord> findByIdAndStudentId(Long id, Long studentId);

    List<ExamRecord> findByExamIdOrderByObjectiveScoreDesc(Long examId);

    List<ExamRecord> findByStudentIdOrderByIdDesc(Long studentId);

    /** 某场考试的全部记录（用于学情分析） */
    List<ExamRecord> findByExamIdAndStatusIn(Long examId, List<RecordStatus> statuses);

    /** 某场考试的全部记录（含未交卷），学情分析按班级过滤时需要一起看 */
    List<ExamRecord> findByExamId(Long examId);

    /** 学生已交卷的记录（用于「我的成绩」） */
    List<ExamRecord> findByStudentIdAndStatusInOrderByIdDesc(Long studentId, List<RecordStatus> statuses);

    long countByExamIdAndStatus(Long examId, RecordStatus status);

    /** 按状态集合计数：用于区分「已交卷」与「已开始未交卷」 */
    long countByExamIdAndStatusIn(Long examId, List<RecordStatus> statuses);

    long countByExamIdAndStudentIdAndStatusIn(Long examId, Long studentId, List<RecordStatus> statuses);

    /**
     * 判断学生是否已有作答记录。
     * <p>
     * 有作答记录的账号不允许物理删除，只能停用：否则成绩单里会出现指向不存在学生的孤儿记录，
     * 且成绩争议时无法追溯。
     */
    long countByStudentId(Long studentId);
}
