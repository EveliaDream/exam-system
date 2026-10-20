package com.finalproject.examsystem.dto.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 手动组卷请求：一次性提交整份试卷的题目与分值。
 * <p>
 * 采用「整体替换」而不是增量增删：组卷是一个整体决策，
 * 整体替换能保证前后端状态一致，也便于在草稿阶段反复调整。
 */
public record ExamQuestionsRequest(

        @NotEmpty(message = "试卷至少需要一道题")
        List<@Valid Item> items) {

    public record Item(

            @NotNull(message = "题目 id 不能为空")
            Long questionId,

            @NotNull(message = "题目分值不能为空")
            @Min(value = 1, message = "题目分值必须大于 0")
            Integer score) {
    }
}
