package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.question.QuestionQuery;
import com.finalproject.examsystem.dto.question.QuestionResponse;
import com.finalproject.examsystem.dto.question.QuestionSaveRequest;
import com.finalproject.examsystem.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 题库管理接口（仅教师）。 */
@RestController
@RequestMapping("/api/questions")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /** 分页查询：支持课程、题型、难度、知识点、关键词、启用状态筛选 */
    @GetMapping
    public Result<PageResult<QuestionResponse>> page(@Valid @ModelAttribute QuestionQuery query) {
        return Result.ok(questionService.page(query));
    }

    @GetMapping("/{id}")
    public Result<QuestionResponse> detail(@PathVariable Long id) {
        return Result.ok(questionService.detail(id));
    }

    @PostMapping
    public Result<QuestionResponse> create(@Valid @RequestBody QuestionSaveRequest request) {
        return Result.ok(questionService.create(request));
    }

    @PutMapping("/{id}")
    public Result<QuestionResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody QuestionSaveRequest request) {
        return Result.ok(questionService.update(id, request));
    }

    /** 删除题目（逻辑删除，同时解除知识点关联） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok();
    }

    /** 启用 / 停用题目 */
    @PutMapping("/{id}/enabled")
    public Result<QuestionResponse> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(questionService.toggleEnabled(id, enabled));
    }
}
