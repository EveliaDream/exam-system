package com.finalproject.examsystem.domain;

/** 考试/GPT 状态机。 */
public enum ExamStatus {
    /** 草稿：可编辑题目与设置，学生不可见 */
    DRAFT("草稿"),
    /** 已发布：学生可见，按时间窗进入 */
    PUBLISHED("已发布"),
    /** 已结束：不可再作答，可查看成绩 */
    FINISHED("已结束");

    private final String label;

    ExamStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
