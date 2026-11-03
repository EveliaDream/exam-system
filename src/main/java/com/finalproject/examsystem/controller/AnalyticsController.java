package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.analytics.ExamAnalyticsResponse;
import com.finalproject.examsystem.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学情分析接口（教师端）。
 * <p>
 * 一次请求返回整块看板数据：成绩统计、分数段分布、单题难度贴合度、
 * 知识点掌握度、学生成绩表。拆成多个接口会让前端并发多次、还要处理部分失败，
 * 而看板本来就是"一次算完、一次画完"。
 */
@RestController
@RequestMapping("/api/analytics/exams")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** 考试学情分析（只能看自己创建的考试） */
    @GetMapping("/{examId}")
    public Result<ExamAnalyticsResponse> analyze(@PathVariable Long examId) {
        return Result.ok(analyticsService.analyzeExam(examId));
    }
}
