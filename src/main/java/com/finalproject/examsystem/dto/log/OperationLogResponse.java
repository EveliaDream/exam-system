package com.finalproject.examsystem.dto.log;

import com.finalproject.examsystem.domain.OperationLog;

import java.time.LocalDateTime;

/** 操作日志列表项。 */
public record OperationLogResponse(Long id,
                                   Long userId,
                                   String username,
                                   String action,
                                   String target,
                                   String ip,
                                   LocalDateTime createdAt) {

    public static OperationLogResponse from(OperationLog log) {
        return new OperationLogResponse(
                log.getId(),
                log.getUserId(),
                log.getUsername(),
                log.getAction(),
                log.getTarget(),
                log.getIp(),
                log.getCreatedAt());
    }
}
