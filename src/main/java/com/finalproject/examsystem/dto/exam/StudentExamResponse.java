package com.finalproject.examsystem.dto.exam;

import com.finalproject.examsystem.domain.Exam;
import com.finalproject.examsystem.domain.ExamPhase;

import java.time.LocalDateTime;

/**
 * 学生端「我的考试」列表项。
 * <p>
 * phase 由服务端按当前时间计算，前端不自己算——避免学生修改系统时间就以为考试"还没开始"。
 * canEnter 集中表达「能否进入作答」的判断依据，供阶段 5 的进入考试接口复用同一套规则。
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
                                  Integer maxAttempt) {

    public static StudentExamResponse from(Exam exam, int questionCount, ExamPhase phase) {
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
                phase == ExamPhase.ONGOING,
                exam.getMaxAttempt());
    }
}
