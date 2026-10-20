package com.finalproject.examsystem.dto.user;

import com.finalproject.examsystem.domain.User;

import java.time.LocalDateTime;

/** 学生列表/详情响应。 */
public record StudentResponse(Long id,
                             String username,
                             String realName,
                             Boolean enabled,
                             Long classId,
                             String className,
                             String grade,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt) {

    /**
     * 由实体转换。
     * <p>
     * 注意这里必须显式挑字段，绝不能直接返回 User 实体：
     * 实体里包含 password（BCrypt 密文）与 tokenVersion，泄露出去既不安全也没必要。
     * 这是"DTO 与实体分离"约定最直接的收益。
     */
    public static StudentResponse from(User user) {
        return new StudentResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getEnabled(),
                user.getStudentClass() == null ? null : user.getStudentClass().getId(),
                user.getStudentClass() == null ? null : user.getStudentClass().getName(),
                user.getStudentClass() == null ? null : user.getStudentClass().getGrade(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
