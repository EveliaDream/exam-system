package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.dto.auth.ChangePasswordRequest;
import com.finalproject.examsystem.dto.auth.LoginRequest;
import com.finalproject.examsystem.dto.auth.LoginResponse;
import com.finalproject.examsystem.dto.auth.UserInfoResponse;
import com.finalproject.examsystem.repository.UserRepository;
import com.finalproject.examsystem.security.JwtTokenProvider;
import com.finalproject.examsystem.security.SecurityUtils;
import com.finalproject.examsystem.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 认证相关业务：登录、获取当前用户、修改密码。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 登录。
     * <p>
     * 用 AuthenticationManager 而不是自己比对密码：这样账号禁用、密码编码器等策略
     * 都由 Spring Security 统一处理，后续加登录失败锁定也不用改这里。
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException e) {
            throw BusinessException.of(ErrorCode.ACCOUNT_DISABLED);
        } catch (AuthenticationException e) {
            // 不区分"用户不存在"与"密码错误"，避免账号枚举
            log.warn("登录失败: username={}", request.username());
            throw BusinessException.of(ErrorCode.BAD_CREDENTIALS);
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findByIdAndDeletedFalse(principal.userId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));

        String token = tokenProvider.generate(
                user.getId(), user.getUsername(), user.getRole().name(), user.getTokenVersion());

        log.info("登录成功: userId={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(
                token,
                tokenProvider.getExpireMillis(),
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole().name(),
                user.getRole().getLabel(),
                user.getStudentClass() == null ? null : user.getStudentClass().getId(),
                user.getStudentClass() == null ? null : user.getStudentClass().getName());
    }

    @Transactional(readOnly = true)
    public UserInfoResponse currentUser() {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        return UserInfoResponse.from(user);
    }

    /**
     * 修改密码。
     * <p>
     * 成功后 tokenVersion +1：已签发的 token 立即失效，必须重新登录。
     * 这是无状态 JWT 下"改密码即下线"的实现方式。
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BusinessException.of(ErrorCode.OLD_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        log.info("用户修改密码: userId={}", userId);
    }

    /**
     * 登出。
     * <p>
     * 无状态 JWT 无法在服务端直接吊销 token，这里由前端丢弃本地 token 完成登出；
     * 如需服务端立即失效，可在改密码流程中复用 tokenVersion 机制。
     */
    public void logout() {
        UserPrincipal principal = SecurityUtils.currentUserOrNull();
        if (principal != null) {
            log.info("用户登出: userId={}", principal.userId());
        }
    }
}
