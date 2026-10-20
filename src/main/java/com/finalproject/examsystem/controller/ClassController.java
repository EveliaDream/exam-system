package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.clazz.ClassQuery;
import com.finalproject.examsystem.dto.clazz.ClassResponse;
import com.finalproject.examsystem.dto.clazz.ClassSaveRequest;
import com.finalproject.examsystem.service.ClassService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 班级管理接口（仅教师）。 */
@RestController
@RequestMapping("/api/classes")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    /** 分页查询班级 */
    @GetMapping
    public Result<PageResult<ClassResponse>> page(@Valid @ModelAttribute ClassQuery query) {
        return Result.ok(classService.pageClasses(query));
    }

    /** 当前教师名下全部班级，供"分配班级"下拉框使用 */
    @GetMapping("/options")
    public Result<List<ClassResponse>> options() {
        return Result.ok(classService.listMyClasses());
    }

    @GetMapping("/{id}")
    public Result<ClassResponse> detail(@PathVariable Long id) {
        return Result.ok(classService.getClass(id));
    }

    @PostMapping
    public Result<ClassResponse> create(@Valid @RequestBody ClassSaveRequest request) {
        return Result.ok(classService.createClass(request));
    }

    @PutMapping("/{id}")
    public Result<ClassResponse> update(@PathVariable Long id,
                                        @Valid @RequestBody ClassSaveRequest request) {
        return Result.ok(classService.updateClass(id, request));
    }

    /** 删除班级；班级内仍有学生时会被拒绝 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        classService.deleteClass(id);
        return Result.ok();
    }
}
