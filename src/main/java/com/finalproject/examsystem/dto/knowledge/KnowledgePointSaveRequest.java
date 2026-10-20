package com.finalproject.examsystem.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/修改知识点请求。 */
public record KnowledgePointSaveRequest(

        @NotBlank(message = "知识点名称不能为空")
        @Size(max = 100, message = "知识点名称不能超过 100 个字符")
        String name,

        @Size(max = 500, message = "说明不能超过 500 个字符")
        String description,

        /** 所属课程；为空表示通用知识点 */
        Long courseId,

        Integer sortOrder) {
}
