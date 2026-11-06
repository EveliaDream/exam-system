package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.dto.question.QuestionQuery;
import com.finalproject.examsystem.dto.question.QuestionResponse;
import com.finalproject.examsystem.dto.question.QuestionSaveRequest;
import com.finalproject.examsystem.dto.user.ImportResult;
import com.finalproject.examsystem.service.ExcelService;
import com.finalproject.examsystem.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/** 题库管理接口（仅教师）。 */
@RestController
@RequestMapping("/api/questions")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final ExcelService excelService;

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

    /** 下载题库导入模板（含示例行与填写说明） */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        ExcelService.ExcelFile file = excelService.questionTemplate();
        return download(file, "xlsx");
    }

    /**
     * 批量导入题目。
     * <p>
     * 逐行容错：成功的行直接入库，失败的行返回行号与原因，教师改完可重新导入。
     * 不用「全部成功或全部回滚」——那样教师无法从错误提示里知道到底哪一行有问题。
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResult> importQuestions(@RequestPart("file") MultipartFile file) {
        return Result.ok(excelService.importQuestions(file));
    }

    /** 统一的下载响应：中文文件名用 RFC 5987 的 filename* 传递，避免乱码 */
    static ResponseEntity<byte[]> download(ExcelService.ExcelFile file, String ext) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "xlsx".equals(ext)
                        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(file.content().length);
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
