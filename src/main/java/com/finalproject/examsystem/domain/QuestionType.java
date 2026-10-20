package com.finalproject.examsystem.domain;

/**
 * 题型。
 * <p>
 * 设计要点：所有题型共用 question 一张表，用该字段区分判分分支，
 * 不要为每种题型建一张表（JOIN 与判分分支会迅速失控）。
 */
public enum QuestionType {

    /** 单选题：answer = ["A"] */
    SINGLE("单选题"),

    /** 多选题：answer = ["A","C"]，判分要求完全一致 */
    MULTIPLE("多选题"),

    /** 判断题：answer = ["T"] 或 ["F"] */
    JUDGE("判断题"),

    /** 填空题：answer = ["答案1","答案2"]，数组内为可接受的备选答案，命中其一即得分 */
    BLANK("填空题");

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
