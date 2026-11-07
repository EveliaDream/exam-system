package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.log.OperationLogQuery;
import com.finalproject.examsystem.dto.log.OperationLogResponse;
import com.finalproject.examsystem.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志接口（教师）。
 * <p>
 * 审计用途：能回答"成绩被人改了怎么追溯"——谁、什么时候、对什么对象做了什么操作。
 * 只读，不提供删除/修改：日志可改就等于没有日志。
 */
@RestController
@RequestMapping("/api/logs")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /** 分页查询：支持动作、关键字（操作人/对象）、时间范围筛选 */
    @GetMapping
    public Result<PageResult<OperationLogResponse>> page(@ModelAttribute OperationLogQuery query) {
        return Result.ok(operationLogService.page(query));
    }

    /** 已出现过的动作类型，供前端下拉筛选 */
    @GetMapping("/actions")
    public Result<List<String>> actions() {
        return Result.ok(operationLogService.actions());
    }
}
