package com.finalproject.examsystem.dto.exam;

import com.finalproject.examsystem.domain.ExamStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 考试列表查询条件（教师端）。 */
public record ExamQuery(

        @Min(value = 1, message = "页码不能小于 1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于 1")
        @Max(value = 100, message = "每页数量不能超过 100")
        Integer size,

        ExamStatus status,

        @Size(max = 100, message = "关键词长度不能超过 100")
        String keyword) {

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
