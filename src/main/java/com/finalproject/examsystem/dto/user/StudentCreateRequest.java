package com.finalproject.examsystem.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新增学生请求。 */
public record StudentCreateRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度需为 3~50 位")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名长度不能超过 50")
        String realName,

        /** 不传则使用系统默认初始密码 */
        @Size(min = 6, max = 64, message = "密码长度需为 6~64 位")
        String password,

        /** 所属班级，可为空（先建号后分班） */
        Long classId) {
}
