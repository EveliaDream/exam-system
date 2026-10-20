package com.finalproject.examsystem.dto.exam;

import com.finalproject.examsystem.domain.Difficulty;
import com.finalproject.examsystem.domain.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 随机组卷请求：按题型 / 难度 / 数量抽取题目，替换当前试卷内容。
 * <p>
 * 注意：随机抽题是「按条件抽」而不是「智能组卷」——
 * 不做知识点覆盖度、难度分布的约束求解（那是 NP 问题，本科阶段做不好）。
 * 抽完由教师人工检查并按需调整，这样既省事又可控。
 */
public record RandomAssembleRequest(

        /** 抽题范围（课程）；为空表示不限课程 */
        Long courseId,

        @NotNull(message = "抽题规则不能为空")
        List<@Valid Rule> rules) {

    public record Rule(

            @NotNull(message = "题型不能为空")
            QuestionType type,

            /** 难度；为空表示不限难度 */
            Difficulty difficulty,

            @NotNull(message = "抽题数量不能为空")
            @Min(value = 1, message = "抽题数量至少为 1")
            @Max(value = 100, message = "单条规则抽题数量不能超过 100")
            Integer count,

            @NotNull(message = "每题分值不能为空")
            @Min(value = 1, message = "每题分值必须大于 0")
            Integer score) {
    }
}
