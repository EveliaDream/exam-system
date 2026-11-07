package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 文件接口：题干配图的读写。
 * <p>
 * 上传限教师；读取放开（见 SecurityConfig 里的 permitAll 说明）。
 * 文件名由服务端生成 UUID，读取接口只接受该格式，因此不存在遍历目录的风险。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /** 上传题干配图，返回可用于 &lt;img src&gt; 的相对地址 */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public Result<FileStorageService.StoredImage> uploadImage(@RequestPart("file") MultipartFile file) {
        return Result.ok(fileStorageService.store(file));
    }

    /**
     * 读取图片。
     * <p>
     * 加了长缓存：图片名带 UUID，内容永不变化，缓存一年是安全的。
     */
    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> read(@PathVariable String filename) {
        byte[] content = fileStorageService.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileStorageService.contentTypeOf(filename)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(content);
    }
}
