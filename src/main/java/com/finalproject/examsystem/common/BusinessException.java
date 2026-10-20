package com.finalproject.examsystem.common;

import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * Service 层校验不通过时抛它，由 {@link GlobalExceptionHandler} 统一转换为 {@link Result}，
 * 不要在 Controller 里到处写 try/catch。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 用于覆盖默认提示，例如"第 3 题分值必须大于 0" */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, String message) {
        return new BusinessException(errorCode, message);
    }
}
