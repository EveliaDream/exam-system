package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 健康检查：用于前端联调时确认后端与代理是否通。 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", "exam-system");
        data.put("time", LocalDateTime.now());
        return Result.ok(data);
    }
}
