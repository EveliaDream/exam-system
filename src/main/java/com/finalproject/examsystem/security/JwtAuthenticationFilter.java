package com.finalproject.examsystem.security;

import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 * <p>
 * 每个请求解析 Authorization 头，校验通过后把认证信息写入 SecurityContext。
 * 双重校验：签名与过期时间（jjwt）+ tokenVersion（与库中比对），后者用于改密码/强制下线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            Claims claims = tokenProvider.parse(token);
            if (claims != null) {
                authenticate(request, claims);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, Claims claims) {
        Object uid = claims.get("uid");
        if (uid == null) {
            return;
        }
        Long userId = Long.valueOf(uid.toString());
        User user = userRepository.findByIdAndDeletedFalse(userId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return;
        }
        // token 版本不一致说明密码已修改或被强制下线，旧 token 立即失效
        Integer versionInToken = claims.get("tv", Integer.class);
        if (versionInToken == null || !versionInToken.equals(user.getTokenVersion())) {
            log.debug("token 版本已失效: userId={}", userId);
            return;
        }
        UserPrincipal principal = UserPrincipal.from(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
