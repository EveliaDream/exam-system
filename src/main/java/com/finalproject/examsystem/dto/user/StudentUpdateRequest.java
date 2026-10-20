package com.finalproject.examsystem.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改学生请求。
 * <p>
 * 不含密码字段：改密码是与"编辑资料"分开的操作（单独的重置密码接口），
 * 避免编辑资料时误改密码，也避免密码字段以明文在列表表单里来回传递。
 */
public record StudentUpdateRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度需为 3~50 位")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名长度不能超过 50")
        String realName,

        /** 所属班级；传 null 表示取消分班 */
        Long classId,

        Boolean enabled) {
}
