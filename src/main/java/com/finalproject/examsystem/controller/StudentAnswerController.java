package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.answer.MyRecordResponse;
import com.finalproject.examsystem.dto.answer.SaveAnswersRequest;
import com.finalproject.examsystem.dto.answer.SubmitResultResponse;
import com.finalproject.examsystem.service.ExamTakingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生作答接口（自动保存、交卷、成绩、切屏上报）。
 * <p>
 * 所有接口都以 recordId 为入口，并在 Service 内校验「该记录属于当前学生」，
 * 学生修改 recordId 也拿不到别人的卷子。
 */
@RestController
@RequestMapping("/api/my/records")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentAnswerController {

    private final ExamTakingService examTakingService;

    /** 我的成绩列表（已交卷的记录） */
    @GetMapping
    public Result<List<MyRecordResponse>> myRecords() {
        return Result.ok(examTakingService.myRecords());
    }

    /**
     * 自动保存本次变更的答案。
     * <p>
     * 响应里的 submitted=true 表示「已超时并被自动交卷」，前端应跳转成绩页。
     */
    @PutMapping("/{recordId}/answers")
    public Result<ExamTakingService.SaveAck> saveAnswers(@PathVariable Long recordId,
                                                         @Valid @RequestBody SaveAnswersRequest request) {
        return Result.ok(examTakingService.saveAnswers(recordId, request));
    }

    /** 交卷并自动判分（幂等：重复提交返回同一结果） */
    @PostMapping("/{recordId}/submit")
    public Result<SubmitResultResponse> submit(@PathVariable Long recordId) {
        return Result.ok(examTakingService.submit(recordId));
    }

    /** 查询本次作答成绩 */
    @GetMapping("/{recordId}/result")
    public Result<SubmitResultResponse> result(@PathVariable Long recordId) {
        return Result.ok(examTakingService.result(recordId));
    }

    /** 切屏次数上报（只留痕，不判作弊） */
    @PostMapping("/{recordId}/switch-screen")
    public Result<Integer> switchScreen(@PathVariable Long recordId) {
        return Result.ok(examTakingService.reportSwitchScreen(recordId));
    }
}
