package com.finalproject.examsystem.domain;

/** 单次作答的状态。 */
public enum RecordStatus {
    /** 已生成试卷但尚未开始计时 */
    NOT_STARTED("未开始"),
    /** 作答中 */
    IN_PROGRESS("作答中"),
    /** 学生主动提交 */
    SUBMITTED("已提交"),
    /** 超时由服务端兜底提交 */
    AUTO_SUBMITTED("超时自动提交");

    private final String label;

    RecordStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 是否已结束作答（提交或超时提交） */
    public boolean isFinished() {
        return this == SUBMITTED || this == AUTO_SUBMITTED;
    }
}
