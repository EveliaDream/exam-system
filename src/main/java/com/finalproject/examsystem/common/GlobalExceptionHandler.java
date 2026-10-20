package com.finalproject.examsystem.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * <p>
 * 目标：Controller 与 Service 只关心业务，任何异常都在这里转成统一 {@link Result}，
 * 前端永远只需要处理一种响应结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按 ErrorCode 自带的 HTTP 状态返回 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(Result.fail(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    /** @Valid 校验失败：把字段级错误拼成一句人话 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = ErrorCode.PARAM_INVALID.getMessage();
        }
        log.warn("参数校验失败: {}", message);
        return build(ErrorCode.PARAM_INVALID, message);
    }

    /** 方法参数上的 @Validated 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("；"));
        return build(ErrorCode.PARAM_INVALID, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.PARAM_INVALID, "缺少必填参数：" + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.PARAM_INVALID, "参数格式不正确：" + ex.getName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    /** 方法级鉴权失败（@PreAuthorize 抛出的 AccessDeniedException） */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("越权访问被拦截: {}", ex.getMessage());
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
    }

    /** 兜底：不把堆栈暴露给前端，但服务端要打全 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("未预期异常", ex);
        return build(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getMessage());
    }

    private ResponseEntity<Result<Void>> build(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode.getCode(), message));
    }
}
