package com.finalproject.examsystem.dto.user;

import java.util.List;

/**
 * 批量导入学生结果。
 * <p>
 * 设计要点：导入**不做全有或全无**。逐行处理，成功多少、失败哪些都如实返回，
 * 否则一个格式错误就会让教师重传整份名单。
 */
public record ImportResult(int total, int success, int failed, List<String> errors) {

    public static ImportResult of(int total, int success, List<String> errors) {
        return new ImportResult(total, success, total - success, List.copyOf(errors));
    }
}
