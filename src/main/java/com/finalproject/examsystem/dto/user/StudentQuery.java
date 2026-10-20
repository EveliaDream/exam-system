package com.finalproject.examsystem.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 学生列表查询条件。
 * <p>
 * 约定：分页页码从 1 开始（与前端 Element Plus 分页组件一致），
 * 由 Service 转成 Spring Data 的 0 基页码，避免每处调用都手工减一。
 */
public record StudentQuery(

        @Min(value = 1, message = "页码不能小于 1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于 1")
        @Max(value = 100, message = "每页数量不能超过 100")
        Integer size,

        /** 按班级筛选，可为空 */
        Long classId,

        /** 关键词：匹配用户名或真实姓名，可为空 */
        @Size(max = 50, message = "关键词长度不能超过 50")
        String keyword,

        /** 是否启用，可为空表示不筛选 */
        Boolean enabled) {

    public int pageIndex() {
        return page == null || page < 1 ? 0 : page - 1;
    }

    public int pageSize() {
        return size == null || size < 1 ? 10 : Math.min(size, 100);
    }

    /** 把空串归一化为 null，避免 JPQL 里 like '%%' 的无效条件 */
    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
