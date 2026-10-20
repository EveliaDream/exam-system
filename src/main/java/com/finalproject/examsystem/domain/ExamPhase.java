package com.finalproject.examsystem.domain;

import java.time.LocalDateTime;

/**
 * 考试在"时间维度"上的阶段（由考试时间窗与服务端当前时间比较得出）。
 * <p>
 * 与 {@link ExamStatus} 的区别：
 * <ul>
 *   <li>{@link ExamStatus} 是教师侧的管理状态（草稿 / 已发布 / 已结束），由人工流转</li>
 *   <li>ExamPhase 是学生侧的时间阶段，完全由时间窗决定，不需要定时任务去翻转</li>
 * </ul>
 * 分开的好处：即使教师忘记手动"结束"考试，只要时间窗过了，学生端也进不去。
 */
public enum ExamPhase {

    NOT_STARTED("未开始"),
    ONGOING("进行中"),
    ENDED("已结束");

    private final String label;

    ExamPhase(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ExamPhase of(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        if (now.isBefore(startTime)) {
            return NOT_STARTED;
        }
        if (now.isAfter(endTime)) {
            return ENDED;
        }
        return ONGOING;
    }
}
