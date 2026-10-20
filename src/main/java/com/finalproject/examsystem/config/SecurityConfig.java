package com.finalproject.examsystem.config;

import com.finalproject.examsystem.security.JwtAuthenticationFilter;
import com.finalproject.examsystem.security.RestAccessDeniedHandler;
import com.finalproject.examsystem.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置（无状态 JWT 模式）。
 * <p>
 * 关键点：
 * <ul>
 *   <li>关闭 CSRF：无 Cookie 会话，无状态 token 不受 CSRF 影响</li>
 *   <li>SessionCreationPolicy.STATELESS：不创建 HttpSession</li>
 *   <li>自定义 401/403 处理器：返回统一 JSON 而非 HTML 登录页</li>
 *   <li>@EnableMethodSecurity：支持 @PreAuthorize("hasRole('TEACHER')") 做方法级鉴权</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 强度 10；BCrypt 自带盐值，绝不要自己写 MD5/SHA1
        return new BCryptPasswordEncoder();
    }

    // 说明：这里不显式声明 DaoAuthenticationProvider。
    // 只要容器里存在 UserDetailsService + PasswordEncoder 两个 bean，
    // Spring Boot 就会自动装配认证提供者；手工声明反而会触发
    // "UserDetailsService beans will not be used ... Consider removing the AuthenticationProvider bean" 警告。

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登录接口与健康检查放行
                        .requestMatchers("/api/auth/login", "/api/health").permitAll()
                        // H2 控制台仅开发期使用
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated())
                // H2 控制台需要允许同源 iframe
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
