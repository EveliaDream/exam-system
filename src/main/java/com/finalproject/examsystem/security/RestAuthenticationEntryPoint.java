package com.finalproject.examsystem.security;

import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未登录访问受保护资源 → 401 + 统一 JSON。
 * <p>
 * 若不自定义，Spring Security 会返回一个 HTML 登录页，前端 Axios 解析会报"格式错误"，
 * 这是前后端分离项目最常见的第一个坑。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(ErrorCode.UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
