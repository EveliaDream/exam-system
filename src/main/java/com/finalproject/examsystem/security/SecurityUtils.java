package com.finalproject.examsystem.security;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户的读取入口。
 * <p>
 * 业务层一律通过它拿 userId，不要在 Controller 里解析 token 或信任前端传的 userId。
 * 这是"权限边界"的第一道防线：谁能看谁的卷子，由服务端身份决定。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 当前登录用户；未登录时抛 401 业务异常 */
    public static UserPrincipal currentUser() {
        UserPrincipal principal = currentUserOrNull();
        if (principal == null) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static UserPrincipal currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public static Long currentUserId() {
        return currentUser().userId();
    }
}
