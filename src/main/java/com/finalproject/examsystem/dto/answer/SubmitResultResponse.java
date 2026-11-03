package com.finalproject.examsystem.dto.answer;

import com.finalproject.examsystem.domain.ExamRecord;

import java.time.LocalDateTime;

/**
 * 交卷结果。
 * <p>
 * 只返回分数与对错数量。逐题正确答案与解析放在「答题详情」接口，
 * 由学生在成绩页主动查看——避免交卷瞬间把整份答案推给前端。
 */
public record SubmitResultResponse(Long recordId,
                                   Long examId,
                                   String examTitle,
                                   Integer objectiveScore,
                                   Integer totalScore,
                                   Integer passScore,
                                   Boolean passed,
                                   Integer correctCount,
                                   Integer questionCount,
                                   Boolean autoSubmitted,
                                   Integer switchScreenCount,
                                   LocalDateTime startTime,
                                   LocalDateTime submitTime) {

    public static SubmitResultResponse from(ExamRecord record,
                                            String examTitle,
                                            int correctCount,
                                            int questionCount,
                                            boolean autoSubmitted) {
        int score = record.getObjectiveScore() == null ? 0 : record.getObjectiveScore();
        Integer pass = record.getExam().getPassScore();
        return new SubmitResultResponse(
                record.getId(),
                record.getExam().getId(),
                examTitle,
                score,
                record.getExam().getTotalScore(),
                pass,
                pass != null && score >= pass,
                correctCount,
                questionCount,
                autoSubmitted,
                record.getSwitchScreenCount(),
                record.getStartTime(),
                record.getSubmitTime());
    }
}
