package com.finalproject.examsystem.dto.exam;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 绑定参考班级请求。 */
public record ClassBindRequest(

        @NotEmpty(message = "请至少选择一个班级")
        List<@NotNull(message = "班级 id 不能为空") Long> classIds) {
}
