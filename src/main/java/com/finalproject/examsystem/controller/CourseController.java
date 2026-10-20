package com.finalproject.examsystem.controller;

import com.finalproject.examsystem.common.Result;
import com.finalproject.examsystem.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程下拉选项。
 * <p>
 * 阶段 3 只提供选项接口（录题与知识点需要按课程归类）；
 * 课程的增删改查留到后续阶段，避免本阶段范围膨胀。
 */
@RestController
@RequestMapping("/api/courses")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class CourseController {

    private final CourseRepository courseRepository;

    @GetMapping("/options")
    public Result<List<Map<String, Object>>> options() {
        List<Map<String, Object>> list = courseRepository.findByDeletedFalseOrderByIdAsc().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("code", c.getCode());
                    return m;
                })
                .toList();
        return Result.ok(list);
    }
}
