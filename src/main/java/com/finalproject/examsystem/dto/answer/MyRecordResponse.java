package com.finalproject.examsystem.dto.answer;

import com.finalproject.examsystem.domain.RecordStatus;

import java.time.LocalDateTime;

/** 学生侧的一次作答记录摘要（用于「我的成绩」列表）。 */
public record MyRecordResponse(Long recordId,
                               Long examId,
                               String examTitle,
                               String courseName,
                               Integer objectiveScore,
                               Integer totalScore,
                               Integer passScore,
                               Boolean passed,
                               RecordStatus status,
                               String statusLabel,
                               Integer attemptNo,
                               LocalDateTime startTime,
                               LocalDateTime submitTime) {
}
