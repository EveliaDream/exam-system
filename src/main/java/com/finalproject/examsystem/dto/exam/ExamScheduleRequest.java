package com.finalproject.examsystem.dto.exam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 调整考试时间窗（已发布的考试也可能需要延长或改期）。
 * <p>
 * 单独成一个接口而不是复用 update：已发布考试的题目与总分必须冻结，
 * 只开放"时间"这一项，避免误改导致成绩口径变化。
 */
public record ExamScheduleRequest(

        @NotNull(message = "开始时间不能为空")
        LocalDateTime startTime,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endTime,

        @NotNull(message = "作答时长不能为空")
        @Min(value = 1, message = "作答时长至少 1 分钟")
        @Max(value = 600, message = "作答时长不能超过 600 分钟")
        Integer duration) {
}
