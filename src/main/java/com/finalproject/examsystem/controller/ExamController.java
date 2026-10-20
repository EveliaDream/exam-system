package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.exam.ClassBindRequest;
import com.finalproject.examsystem.dto.exam.ExamDetailResponse;
import com.finalproject.examsystem.dto.exam.ExamQuery;
import com.finalproject.examsystem.dto.exam.ExamQuestionsRequest;
import com.finalproject.examsystem.dto.exam.ExamResponse;
import com.finalproject.examsystem.dto.exam.ExamSaveRequest;
import com.finalproject.examsystem.dto.exam.ExamScheduleRequest;
import com.finalproject.examsystem.dto.exam.RandomAssembleRequest;
import com.finalproject.examsystem.service.ExamService;
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

/**
 * 考试与组卷接口（仅教师）。
 * <p>
 * 状态流转只有一条合法路径：DRAFT → PUBLISHED → FINISHED。
 * 每个写操作都在 Service 内校验当前状态，避免"发布后还能改题"这类破坏成绩口径的操作。
 */
@RestController
@RequestMapping("/api/exams")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /** 考试列表：只返回当前教师创建的考试 */
    @GetMapping
    public Result<PageResult<ExamResponse>> page(@Valid @ModelAttribute ExamQuery query) {
        return Result.ok(examService.page(query));
    }

    /** 考试详情：含题目快照、绑定班级、分值一致性标记 */
    @GetMapping("/{id}")
    public Result<ExamDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(examService.detail(id));
    }

    /** 新建考试草稿 */
    @PostMapping
    public Result<ExamResponse> create(@Valid @RequestBody ExamSaveRequest request) {
        return Result.ok(examService.create(request));
    }

    /** 修改草稿（题目以外的设置） */
    @PutMapping("/{id}")
    public Result<ExamResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody ExamSaveRequest request) {
        return Result.ok(examService.update(id, request));
    }

    /** 调整时间窗：已发布的考试也可调用，但题目与总分冻结 */
    @PutMapping("/{id}/schedule")
    public Result<ExamResponse> updateSchedule(@PathVariable Long id,
                                               @Valid @RequestBody ExamScheduleRequest request) {
        return Result.ok(examService.updateSchedule(id, request));
    }

    /** 删除考试：仅草稿可删 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        examService.delete(id);
        return Result.ok();
    }

    /** 手动组卷：整体替换试卷题目并生成快照 */
    @PutMapping("/{id}/questions")
    public Result<ExamDetailResponse> setQuestions(@PathVariable Long id,
                                                   @Valid @RequestBody ExamQuestionsRequest request) {
        return Result.ok(examService.setQuestions(id, request));
    }

    /** 随机组卷：按题型/难度/数量抽题，替换当前试卷 */
    @PostMapping("/{id}/questions/random")
    public Result<ExamDetailResponse> randomAssemble(@PathVariable Long id,
                                                     @Valid @RequestBody RandomAssembleRequest request) {
        return Result.ok(examService.randomAssemble(id, request));
    }

    /** 绑定参考班级：班级必须属于当前教师 */
    @PutMapping("/{id}/classes")
    public Result<ExamDetailResponse> bindClasses(@PathVariable Long id,
                                                  @Valid @RequestBody ClassBindRequest request) {
        return Result.ok(examService.bindClasses(id, request));
    }

    /** 发布：校验题目、分值之和、班级、时间窗，任一不满足都拒绝 */
    @PutMapping("/{id}/publish")
    public Result<ExamResponse> publish(@PathVariable Long id) {
        return Result.ok(examService.publish(id));
    }

    /** 结束考试 */
    @PutMapping("/{id}/finish")
    public Result<ExamResponse> finish(@PathVariable Long id) {
        return Result.ok(examService.finish(id));
    }
}
