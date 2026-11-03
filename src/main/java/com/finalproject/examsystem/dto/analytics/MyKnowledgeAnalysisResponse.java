package com.finalproject.examsystem.dto.analytics;

import java.util.List;

/**
 * 学生端学情分析：知识点掌握度 + 薄弱知识点推送。
 * <p>
 * 掌握度按**分值加权**：`Σ(该知识点下答对题的分值) / Σ(该知识点下全部题的分值)`。
 * 不按题目数量算，是因为同一知识点下可能既有 5 分的题也有 10 分的综合题，
 * 按题数平均会让分值权重失真。
 */
public record MyKnowledgeAnalysisResponse(
        Long recordId,
        String examTitle,
        Integer score,
        Integer totalScore,
        Integer passScore,
        Boolean passed,
        /** 薄弱知识点（掌握度升序取前 N，最多 5 个） */
        List<KnowledgeMastery> weakPoints,
        /** 本场考试涉及的全部知识点，按掌握度升序 */
        List<KnowledgeMastery> allPoints,
        /** 是否有足够作答量做判断（作答知识点数 > 0） */
        Boolean hasData) {

    /**
     * 单个知识点的掌握情况。
     *
     * @param wrongCount 该知识点下答错的题数——同时给出百分比与题数，
     *                   学生既知道"哪块最弱"，也知道"弱到什么程度"
     */
    public record KnowledgeMastery(
            Long knowledgePointId,
            String name,
            Double mastery,
            int correctCount,
            int wrongCount,
            int questionCount) {
    }

    /** 薄弱判定阈值：掌握度低于该值视为需要加强 */
    public static final double WEAK_THRESHOLD = 0.6;

    /** 薄弱知识点最多推送几个 */
    public static final int MAX_WEAK_POINTS = 5;
}
