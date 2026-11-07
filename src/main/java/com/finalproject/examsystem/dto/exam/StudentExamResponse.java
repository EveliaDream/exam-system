package com.finalproject.examsystem.dto.exam;

import com.finalproject.examsystem.domain.Exam;
import com.finalproject.examsystem.domain.ExamPhase;

import java.time.LocalDateTime;

/**
 * 学生端「我的考试」列表项。
 * <p>
 * phase 由服务端按当前时间计算，前端不自己算——避免学生修改系统时间就以为考试"还没开始"。
 * canEnter 集中表达「能否进入作答」的判断依据，进入考试接口复用同一套规则。
 * <p>
 * 多轮考试相关字段（usedAttempts / maxAttempt / inProgress / lastRecordId）都由服务端算好，
 * 前端只负责展示与跳转，不自己推断"还能不能考"。
 */
public record StudentExamResponse(Long id,
                                  String title,
                                  String description,
                                  String courseName,
                                  Integer totalScore,
                                  Integer passScore,
                                  Integer questionCount,
                                  Integer duration,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  ExamPhase phase,
                                  String phaseLabel,
                                  Boolean canEnter,
                                  Integer maxAttempt,
                                  /** 已交卷次数 */
                                  Integer usedAttempts,
                                  /** 是否有进行中的作答（进入即继续） */
                                  Boolean inProgress,
                                  /** 最近一次作答记录 id，用于「查看成绩」 */
                                  Long lastRecordId) {

    public static StudentExamResponse from(Exam exam, int questionCount, ExamPhase phase,
                                           int usedAttempts, boolean inProgress, Long lastRecordId) {
        int max = exam.getMaxAttempt() == null ? 1 : exam.getMaxAttempt();
        // 时间窗内 + （有未完成的作答 或 还有剩余次数）才能进入
        boolean canEnter = phase == ExamPhase.ONGOING && (inProgress || usedAttempts < max);
        return new StudentExamResponse(
                exam.getId(),
                exam.getTitle(),
                exam.getDescription(),
                exam.getCourse() == null ? null : exam.getCourse().getName(),
                exam.getTotalScore(),
                exam.getPassScore(),
                questionCount,
                exam.getDuration(),
                exam.getStartTime(),
                exam.getEndTime(),
                phase,
                phase.getLabel(),
                canEnter,
                max,
                usedAttempts,
                inProgress,
                lastRecordId);
    }
}
