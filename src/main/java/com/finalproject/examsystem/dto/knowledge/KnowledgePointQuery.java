package com.finalproject.examsystem.dto.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 知识点列表查询条件。 */
public record KnowledgePointQuery(

        @Min(value = 1, message = "页码不能小于 1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于 1")
        @Max(value = 200, message = "每页数量不能超过 200")
        Integer size,

        /** 按课程筛选；为空表示全部（含通用知识点） */
        Long courseId,

        @Size(max = 100, message = "关键词长度不能超过 100")
        String keyword) {

    public int pageIndex() {
        return page == null || page < 1 ? 0 : page - 1;
    }

    public int pageSize() {
        return size == null || size < 1 ? 50 : Math.min(size, 200);
    }

    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
