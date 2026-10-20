package com.finalproject.examsystem.dto.exam;

import com.finalproject.examsystem.domain.ExamStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试详情（教师端编辑页使用）。
 * <p>
 * scoreMatched = 题目分值之和是否等于试卷总分。
 * 前端据此实时提示教师，避免组完卷才发现无法发布。
 */
public record ExamDetailResponse(Long id,
                                 String title,
                                 String description,
                                 Long courseId,
                                 String courseName,
                                 Integer totalScore,
                                 Integer passScore,
                                 Integer questionScoreSum,
                                 Integer questionCount,
                                 Boolean scoreMatched,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime,
                                 Integer duration,
                                 Boolean shuffleQuestion,
                                 Boolean shuffleOption,
                                 Integer maxAttempt,
                                 ExamStatus status,
                                 String statusLabel,
                                 List<ClassBrief> classes,
                                 List<ExamQuestionView> questions,
                                 LocalDateTime createdAt) {

    /** 绑定的班级（考试发布范围）。grade 沿用实体的字符串类型（如 "2023"） */
    public record ClassBrief(Long id, String name, String grade) {
    }
}
