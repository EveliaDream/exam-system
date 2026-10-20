package com.finalproject.examsystem.domain;

/** 难度等级，用于组卷时按难度随机抽题。 */
public enum Difficulty {
    EASY("简单"),
    MEDIUM("中等"),
    HARD("困难");

    private final String label;

    Difficulty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
