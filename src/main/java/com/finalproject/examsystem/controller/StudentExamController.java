package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.exam.StudentExamResponse;
import com.finalproject.examsystem.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端考试接口。
 * <p>
 * 只暴露「自己的考试」：数据范围由当前登录学生的班级决定，
 * 学生无法通过改参数看到别的班级的考试。
 */
@RestController
@RequestMapping("/api/my/exams")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentExamController {

    private final ExamService examService;

    /**
     * 我的考试列表。
     * <p>
     * 返回的 phase（未开始 / 进行中 / 已结束）由服务端按当前时间计算，
     * 前端不自己判断——否则学生改系统时间就能绕过时间窗。
     */
    @GetMapping
    public Result<List<StudentExamResponse>> myExams() {
        return Result.ok(examService.myExams());
    }
}
