package com.finalproject.examsystem.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 * <p>
 * 编码规范（照抄即可，后续模块按段位追加）：
 * <pre>
 * 1xxxx 通用/参数      20001 参数校验失败
 * 2xxxx 认证与权限     40100 未登录  40300 无权限
 * 3xxxx 用户与班级
 * 4xxxx 题库
 * 5xxxx 考试与组卷
 * 6xxxx 作答与判分
 * </pre>
 */
@Getter
public enum ErrorCode {

    // ===== 1xxxx 通用 =====
    PARAM_INVALID(20001, "参数校验失败", HttpStatus.BAD_REQUEST),
    SYSTEM_ERROR(20000, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    NOT_FOUND(20002, "请求的资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(20003, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),

    // ===== 2xxxx 认证与权限 =====
    UNAUTHORIZED(40100, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(40102, "账号已被禁用，请联系教师", HttpStatus.FORBIDDEN),
    TOKEN_INVALID(40103, "登录凭证无效，请重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "无权限访问该资源", HttpStatus.FORBIDDEN),

    // ===== 3xxxx 用户与班级 =====
    USER_NOT_FOUND(30100, "用户不存在", HttpStatus.NOT_FOUND),
    USERNAME_DUPLICATED(30101, "用户名已存在", HttpStatus.CONFLICT),
    OLD_PASSWORD_MISMATCH(30102, "原密码不正确", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_SELF(30103, "不能删除当前登录账号", HttpStatus.BAD_REQUEST),
    NOT_A_STUDENT(30104, "该账号不是学生，无法执行此操作", HttpStatus.BAD_REQUEST),
    CLASS_NOT_FOUND(30200, "班级不存在", HttpStatus.NOT_FOUND),
    CLASS_NAME_DUPLICATED(30201, "班级名称已存在", HttpStatus.CONFLICT),
    CLASS_HAS_STUDENTS(30202, "该班级下仍有学生，无法删除", HttpStatus.CONFLICT),
    CLASS_NOT_OWNED(30203, "该班级不属于当前教师，无权操作", HttpStatus.FORBIDDEN),

    // ===== 4xxxx 题库 =====
    QUESTION_NOT_FOUND(40104, "题目不存在", HttpStatus.NOT_FOUND),
    QUESTION_IN_USE(40105, "题目已被考试引用，无法删除", HttpStatus.CONFLICT),
    QUESTION_INVALID(40106, "题目内容不合法", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_POINT_NOT_FOUND(40107, "知识点不存在", HttpStatus.NOT_FOUND),
    KNOWLEDGE_POINT_DUPLICATED(40108, "同一课程下已存在同名知识点", HttpStatus.CONFLICT),
    KNOWLEDGE_POINT_IN_USE(40109, "该知识点已被题目引用，无法删除", HttpStatus.CONFLICT),

    // ===== 5xxxx 考试 =====
    EXAM_NOT_FOUND(50100, "考试不存在", HttpStatus.NOT_FOUND),
    EXAM_SCORE_MISMATCH(50101, "题目分值之和与试卷总分不一致", HttpStatus.BAD_REQUEST),
    EXAM_NOT_STARTED(50102, "考试尚未开始", HttpStatus.BAD_REQUEST),
    EXAM_ENDED(50103, "考试已结束", HttpStatus.BAD_REQUEST),

    // ===== 6xxxx 作答 =====
    RECORD_NOT_FOUND(60100, "答题记录不存在", HttpStatus.NOT_FOUND),
    ALREADY_SUBMITTED(60101, "该考试已提交，不能重复作答", HttpStatus.CONFLICT),
    ATTEMPT_LIMIT_EXCEEDED(60102, "已达到该考试的最大作答次数", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
