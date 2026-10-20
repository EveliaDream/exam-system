package com.finalproject.examsystem.common;

import java.util.List;

/**
 * 统一分页响应体。
 * <p>
 * 约定：所有列表接口统一返回 { list, total, page, size }，前端表格组件直接绑定。
 */
public record PageResult<T>(List<T> list, long total, int page, int size) {

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, page, size);
    }
}
