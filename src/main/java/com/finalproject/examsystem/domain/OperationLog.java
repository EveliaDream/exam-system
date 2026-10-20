package com.finalproject.examsystem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志。
 * <p>
 * 实现成本极低但答辩加分明显：能回答"成绩被人改了怎么追溯"这类问题。
 */
@Entity
@Table(name = "operation_log")
@Getter
@Setter
@NoArgsConstructor
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    /** 动作，如 LOGIN / SUBMIT_EXAM / CREATE_QUESTION */
    @Column(nullable = false, length = 50)
    private String action;

    /** 操作对象描述，如 "exam#12" */
    @Column(length = 200)
    private String target;

    @Column(length = 50)
    private String ip;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public static OperationLog of(Long userId, String username, String action, String target, String ip) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setTarget(target);
        log.setIp(ip);
        return log;
    }
}
