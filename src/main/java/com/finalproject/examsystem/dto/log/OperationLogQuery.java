package com.finalproject.examsystem.dto.log;

/**
 * 操作日志查询参数。
 * <p>
 * 时间范围用字符串接收再解析：与项目其他查询接口保持一致（前端统一传 yyyy-MM-dd HH:mm:ss），
 * 也避免时区解析分散到各处。
 */
public record OperationLogQuery(Integer page, Integer size, String action, String keyword,
                                String from, String to) {

    public int pageIndex() {
        return page == null || page < 1 ? 0 : page - 1;
    }

    public int pageSize() {
        int s = size == null ? 20 : size;
        return Math.min(Math.max(s, 1), 100);
    }

    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public String normalizedAction() {
        return action == null || action.isBlank() ? null : action.trim();
    }
}
