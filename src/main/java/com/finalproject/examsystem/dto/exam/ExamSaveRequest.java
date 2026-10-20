package com.finalproject.examsystem.dto.exam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 新增/修改考试（草稿）请求。
 * <p>
 * 时间格式沿用全局配置：{@code yyyy-MM-dd HH:mm:ss}。
 * 注意 totalScore 是教师设定的目标总分，发布时会被强校验：
 * 必须等于组卷题目分值之和，否则拒绝发布（避免总分与题目对不上导致学生端显示错误）。
 */
public record ExamSaveRequest(

        @NotBlank(message = "考试标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 个字符")
        String title,

        @Size(max = 2000, message = "说明不能超过 2000 个字符")
        String description,

        Long courseId,

        @NotNull(message = "试卷总分不能为空")
        @Min(value = 1, message = "试卷总分必须大于 0")
        @Max(value = 1000, message = "试卷总分不能超过 1000")
        Integer totalScore,

        @NotNull(message = "及格线不能为空")
        @Min(value = 0, message = "及格线不能为负")
        Integer passScore,

        @NotNull(message = "开始时间不能为空")
        LocalDateTime startTime,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endTime,

        /** 作答时长（分钟）；实际截止时间 = min(开始作答 + duration, endTime) */
        @NotNull(message = "作答时长不能为空")
        @Min(value = 1, message = "作答时长至少 1 分钟")
        @Max(value = 600, message = "作答时长不能超过 600 分钟")
        Integer duration,

        Boolean shuffleQuestion,

        Boolean shuffleOption,

        @Min(value = 1, message = "最大作答次数至少为 1")
        @Max(value = 10, message = "最大作答次数不能超过 10")
        Integer maxAttempt) {
}
