package com.finalproject.examsystem.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置。
 * <p>
 * 目前只做一件事：注册操作日志拦截器（仅拦 /api/**，静态资源不记）。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final OperationLogInterceptor operationLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns("/api/**");
    }
}
