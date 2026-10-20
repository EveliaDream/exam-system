package com.finalproject.examsystem.dto.exam;

import com.finalproject.examsystem.domain.Exam;
import com.finalproject.examsystem.domain.ExamStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试列表项。
 * <p>
 * 额外返回 questionCount 与 questionScoreSum：
 * 教师能直接看到「题目分值之和」与设定的「试卷总分」是否一致，
 * 不必等到点发布才被拒绝（发布时会强校验两者相等）。
 */
public record ExamResponse(Long id,
                           String title,
                           String description,
                           Long courseId,
                           String courseName,
                           Integer totalScore,
                           Integer passScore,
                           Integer questionCount,
                           Integer questionScoreSum,
                           LocalDateTime startTime,
                           LocalDateTime endTime,
                           Integer duration,
                           Boolean shuffleQuestion,
                           Boolean shuffleOption,
                           Integer maxAttempt,
                           ExamStatus status,
                           String statusLabel,
                           List<String> classNames,
                           LocalDateTime createdAt) {

    public static ExamResponse from(Exam exam, int questionCount, int questionScoreSum,
                                    List<String> classNames) {
        return new ExamResponse(
                exam.getId(),
                exam.getTitle(),
                exam.getDescription(),
                exam.getCourse() == null ? null : exam.getCourse().getId(),
                exam.getCourse() == null ? null : exam.getCourse().getName(),
                exam.getTotalScore(),
                exam.getPassScore(),
                questionCount,
                questionScoreSum,
                exam.getStartTime(),
                exam.getEndTime(),
                exam.getDuration(),
                exam.getShuffleQuestion(),
                exam.getShuffleOption(),
                exam.getMaxAttempt(),
                exam.getStatus(),
                exam.getStatus() == null ? null : exam.getStatus().getLabel(),
                classNames == null ? List.of() : classNames,
                exam.getCreatedAt());
    }
}
