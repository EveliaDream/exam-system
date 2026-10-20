package com.finalproject.examsystem.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一 JSON 中的时间格式。
 * <p>
 * 背景：application.yml 里的 spring.jackson.date-format 只对 java.util.Date 生效，
 * LocalDateTime 默认会序列化成 2026-09-18T09:58:09.201718 这种带微秒的 ISO 格式，
 * 前端展示与排序都不方便，因此在这里显式注册格式化器。
 * <p>
 * 注意：Boot 4 用的是 Jackson 3（包名 tools.jackson），且 java.time 支持已内置在 jackson-databind 中，
 * 不需要再引入 jackson-datatype-jsr310。
 * 配套约定：实体字段只存"本地时间"，系统时区由 spring.jackson.time-zone 统一为 Asia/Shanghai。
 */
@Configuration
public class JacksonConfig {

    /** 全项目统一的时间格式，前端 Element Plus 可直接绑定 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    @Bean
    public JsonMapperBuilderCustomizer dateTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("exam-system-javatime");
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));
            builder.addModule(module);
        };
    }
}
