package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.knowledge.KnowledgePointQuery;
import com.finalproject.examsystem.dto.knowledge.KnowledgePointResponse;
import com.finalproject.examsystem.dto.knowledge.KnowledgePointSaveRequest;
import com.finalproject.examsystem.service.KnowledgePointService;
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

import java.util.List;

/**
 * 知识点管理接口（仅教师）。
 * <p>
 * 知识点是学情分析的主干，因此独立成模块而不是题目上的一个文本框：
 * 只有可枚举、可关联、可统计的知识点，才能支撑「按知识点聚合掌握度」。
 */
@RestController
@RequestMapping("/api/knowledge-points")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    /** 分页查询知识点，附带被引用题数 */
    @GetMapping
    public Result<PageResult<KnowledgePointResponse>> page(@Valid @ModelAttribute KnowledgePointQuery query) {
        return Result.ok(knowledgePointService.page(query));
    }

    /** 下拉选项（不分页），供录题时选择知识点 */
    @GetMapping("/options")
    public Result<List<KnowledgePointResponse>> options(
            @RequestParam(required = false) Long courseId) {
        return Result.ok(knowledgePointService.options(courseId));
    }

    @GetMapping("/{id}")
    public Result<KnowledgePointResponse> detail(@PathVariable Long id) {
        return Result.ok(knowledgePointService.detail(id));
    }

    @PostMapping
    public Result<KnowledgePointResponse> create(@Valid @RequestBody KnowledgePointSaveRequest request) {
        return Result.ok(knowledgePointService.create(request));
    }

    @PutMapping("/{id}")
    public Result<KnowledgePointResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody KnowledgePointSaveRequest request) {
        return Result.ok(knowledgePointService.update(id, request));
    }

    /** 删除知识点；被题目引用时拒绝，提示先解除关联 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgePointService.delete(id);
        return Result.ok();
    }
}
