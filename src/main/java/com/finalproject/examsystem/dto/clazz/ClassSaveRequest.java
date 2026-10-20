package com.finalproject.examsystem.dto.clazz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/修改班级请求（字段完全一致，合并为一个 record）。 */
public record ClassSaveRequest(

        @NotBlank(message = "班级名称不能为空")
        @Size(max = 50, message = "班级名称长度不能超过 50")
        String name,

        @Size(max = 20, message = "年级长度不能超过 20")
        String grade) {
}
