package com.finalproject.examsystem.dto.auth;

/** 登录响应：token + 前端需要的用户信息（不含密码）。 */
public record LoginResponse(String token,
                            long expireMillis,
                            Long userId,
                            String username,
                            String realName,
                            String role,
                            String roleLabel,
                            Long classId,
                            String className) {
}
