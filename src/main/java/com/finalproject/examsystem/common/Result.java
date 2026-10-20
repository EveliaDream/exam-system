package com.finalproject.examsystem.common;

/**
 * 统一响应体。
 * <p>
 * 约定：{ code, message, data }
 * code = 0 表示成功，非 0 表示业务失败（见 {@link ErrorCode}）。
 * 前端 Axios 拦截器只认这一种结构，所有 Controller 必须返回它。
 */
public record Result<T>(int code, String message, T data) {

    public static final int SUCCESS_CODE = 0;

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    public static Result<Void> ok() {
        return new Result<>(SUCCESS_CODE, "success", null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
