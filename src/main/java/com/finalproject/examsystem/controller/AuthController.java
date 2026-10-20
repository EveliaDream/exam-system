package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.auth.ChangePasswordRequest;
import com.finalproject.examsystem.dto.auth.LoginRequest;
import com.finalproject.examsystem.dto.auth.LoginResponse;
import com.finalproject.examsystem.dto.auth.UserInfoResponse;
import com.finalproject.examsystem.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 * <p>
 * 统一返回 {@link Result}，前端 Axios 拦截器按 code 统一处理。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录：唯一放行的接口，成功后返回 JWT */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 当前登录用户：前端刷新页面后用它恢复登录态 */
    @GetMapping("/me")
    public Result<UserInfoResponse> me() {
        return Result.ok(authService.currentUser());
    }

    /** 登出：无状态 JWT 由前端丢弃本地 token，服务端仅记录日志 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    /** 修改密码：成功后 tokenVersion +1，所有已签发 token 失效 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }
}
