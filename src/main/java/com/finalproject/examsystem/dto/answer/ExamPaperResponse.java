package com.finalproject.examsystem.dto.answer;

import com.finalproject.examsystem.domain.RecordStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷（学生作答视图）。
 * <p>
 * 关键字段 deadline 与 remainingSeconds 都由**服务端**计算并下发：
 * 前端倒计时只用于显示，真正的时间校验在每次保存与交卷时重新做。
 * 学生即使修改本机时间，也无法延长作答时间。
 */
public record ExamPaperResponse(Long recordId,
                                Long examId,
                                String title,
                                String description,
                                Integer totalScore,
                                Integer passScore,
                                Integer duration,
                                LocalDateTime startTime,
                                LocalDateTime deadline,
                                Long remainingSeconds,
                                Boolean shuffleQuestion,
                                Boolean shuffleOption,
                                Integer attemptNo,
                                RecordStatus status,
                                Integer switchScreenCount,
                                List<PaperQuestionResponse> questions,
                                List<SavedAnswerView> savedAnswers) {
}
