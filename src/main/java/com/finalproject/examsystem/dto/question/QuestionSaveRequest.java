package com.finalproject.examsystem.dto.question;

import com.finalproject.examsystem.domain.Difficulty;
import com.finalproject.examsystem.domain.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增/修改题目请求。
 * <p>
 * 题型与答案结构约定（必须由 Service 校验）：
 * SINGLE   options 至少 2 项，answer 恰好 1 项且为合法选项标识（A/B/C…）
 * MULTIPLE options 至少 2 项，answer 至少 2 项，元素不重复
 * JUDGE    options 固定为 ["正确","错误"]，answer 为 ["T"] 或 ["F"]
 * BLANK    options 为空，answer 为可接受答案列表（至少 1 项）
 */
public record QuestionSaveRequest(

        @NotNull(message = "题型不能为空")
        QuestionType type,

        @NotBlank(message = "题干不能为空")
        @Size(max = 2000, message = "题干不能超过 2000 个字符")
        String stem,

        List<String> options,

        List<String> answer,

        @NotNull(message = "分值不能为空")
        @Min(value = 1, message = "分值必须大于 0")
        @Max(value = 100, message = "单题分值不能超过 100")
        Integer score,

        @NotNull(message = "难度不能为空")
        Difficulty difficulty,

        @Size(max = 2000, message = "解析不能超过 2000 个字符")
        String analysis,

        Long courseId,

        /** 关联的知识点 id 列表（一道题可挂多个） */
        List<Long> knowledgePointIds,

        Boolean enabled) {
}
