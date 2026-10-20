package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.domain.Role;
import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.repository.UserRepository;
import com.finalproject.examsystem.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户相关接口。
 * <p>
 * 阶段 0 只放一个"个人信息"，它的作用是提供一个教师专属接口，
 * 用来验证权限边界确实生效：学生带 token 访问应返回 403。
 * 阶段 2 再补学生 CRUD、班级管理。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * 教师专属：查看任意用户资料。
     * <p>
     * 方法级鉴权用 @PreAuthorize，越权时由 RestAccessDeniedHandler 返回统一 403 JSON。
     * 注意：接口鉴权必须在服务端做，前端路由守卫只能算体验优化。
     */
    @GetMapping("/profile")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Map<String, Object>> teacherProfile() {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.findByIdAndDeletedFalse(userId).orElseThrow();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("role", user.getRole().name());
        data.put("roleLabel", user.getRole().getLabel());
        data.put("isTeacher", user.getRole() == Role.TEACHER);
        data.put("message", "这是仅教师可访问的接口");
        return Result.ok(data);
    }
}
