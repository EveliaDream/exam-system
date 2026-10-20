package com.finalproject.examsystem.dto.auth;

import com.finalproject.examsystem.domain.User;

/**
 * 当前登录用户信息。
 * <p>
 * 前端刷新页面后用 /api/auth/me 恢复登录态，避免把用户信息只存在 localStorage 里被随意篡改。
 */
public record UserInfoResponse(Long userId,
                              String username,
                              String realName,
                              String role,
                              String roleLabel,
                              Long classId,
                              String className) {

    public static UserInfoResponse from(User user) {
        Long classId = user.getStudentClass() == null ? null : user.getStudentClass().getId();
        String className = user.getStudentClass() == null ? null : user.getStudentClass().getName();
        return new UserInfoResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole().name(),
                user.getRole().getLabel(),
                classId,
                className);
    }
}
