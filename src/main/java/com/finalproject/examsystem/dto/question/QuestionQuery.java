package com.finalproject.examsystem.dto.question;

import com.finalproject.examsystem.domain.Difficulty;
import com.finalproject.examsystem.domain.QuestionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 题目列表查询条件。
 * <p>
 * 支持按知识点筛选：这是学情分析的入口——教师要能定位「某个知识点下有哪些题」。
 */
public record QuestionQuery(

        @Min(value = 1, message = "页码不能小于 1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于 1")
        @Max(value = 100, message = "每页数量不能超过 100")
        Integer size,

        Long courseId,

        QuestionType type,

        Difficulty difficulty,

        /** 按知识点筛选 */
        Long knowledgePointId,

        @Size(max = 100, message = "关键词长度不能超过 100")
        String keyword,

        Boolean enabled) {

    public int pageIndex() {
        return page == null || page < 1 ? 0 : page - 1;
    }

    public int pageSize() {
        return size == null || size < 1 ? 10 : Math.min(size, 100);
    }

    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
