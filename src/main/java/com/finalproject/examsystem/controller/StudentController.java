package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.user.ImportResult;
import com.finalproject.examsystem.dto.user.StudentCreateRequest;
import com.finalproject.examsystem.dto.user.StudentQuery;
import com.finalproject.examsystem.dto.user.StudentResponse;
import com.finalproject.examsystem.dto.user.StudentUpdateRequest;
import com.finalproject.examsystem.service.UserService;
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

/**
 * 学生管理接口。
 * <p>
 * 类级 {@code @PreAuthorize} 保证整个模块只有教师可访问：
 * 比在每个方法上重复标注更不容易漏，且学生即使拿到接口地址也会被 403 拦截。
 */
@RestController
@RequestMapping("/api/students")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class StudentController {

    private final UserService userService;

    /** 分页查询学生：支持按班级、关键词、状态筛选 */
    @GetMapping
    public Result<PageResult<StudentResponse>> page(@Valid @ModelAttribute StudentQuery query) {
        return Result.ok(userService.pageStudents(query));
    }

    /** 学生详情 */
    @GetMapping("/{id}")
    public Result<StudentResponse> detail(@PathVariable Long id) {
        return Result.ok(userService.getStudent(id));
    }

    /** 新增学生 */
    @PostMapping
    public Result<StudentResponse> create(@Valid @RequestBody StudentCreateRequest request) {
        return Result.ok(userService.createStudent(request));
    }

    /** 修改学生资料（含调班、启用/停用） */
    @PutMapping("/{id}")
    public Result<StudentResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody StudentUpdateRequest request) {
        return Result.ok(userService.updateStudent(id, request));
    }

    /** 删除学生（逻辑删除；已有作答记录的学生会被拒绝，应改为停用） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteStudent(id);
        return Result.ok();
    }

    /** 重置密码为系统初始密码，并使其已签发的 token 立即失效 */
    @PutMapping("/{id}/password/reset")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.ok();
    }

    /**
     * 批量导入学生。
     * <p>
     * 请求体为纯文本，每行一条：{@code 用户名,姓名,年级,班级名称}
     * 用 text/plain 而不是 multipart 上传：教师直接从 Excel 复制一列粘贴即可，
     * 不需要先另存为文件，少一步操作。
     */
    @PostMapping(value = "/import", consumes = "text/plain;charset=UTF-8")
    public Result<ImportResult> importStudents(@RequestBody String content) {
        return Result.ok(userService.importStudents(content));
    }
}
