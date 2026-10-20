package com.finalproject.examsystem.dto.knowledge;

import com.finalproject.examsystem.domain.KnowledgePoint;

/**
 * 知识点响应。
 * <p>
 * 附带 questionCount（被多少道题引用）：教师在删除知识点前能先看到影响面，
 * 也为后续「知识点覆盖是否均衡」的分析留出数据入口。
 */
public record KnowledgePointResponse(Long id,
                                     String name,
                                     String description,
                                     Long courseId,
                                     Integer sortOrder,
                                     Boolean enabled,
                                     long questionCount) {

    public static KnowledgePointResponse from(KnowledgePoint kp, long questionCount) {
        return new KnowledgePointResponse(
                kp.getId(),
                kp.getName(),
                kp.getDescription(),
                kp.getCourseId(),
                kp.getSortOrder(),
                kp.getEnabled(),
                questionCount);
    }
}
