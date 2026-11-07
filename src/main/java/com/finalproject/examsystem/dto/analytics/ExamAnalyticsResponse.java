package com.finalproject.examsystem.dto.analytics;

import java.util.List;

/**
 * 教师端学情分析看板（一场考试一次请求返回全部区块）。
 * <p>
 * 为什么合成一个响应而不是拆多个接口：看板是一次性渲染的，
 * 拆开会让前端并发 4 个请求、还要处理部分失败；这里一次算完，前端只画。
 * <p>
 * <b>sufficient（样本是否充足）</b>：见 {@link #MIN_SAMPLE_NOTE}。
 * 样本不足时照常返回统计数字，但所有"判断类"结论都会标注为仅供参考，
 * 而不是直接编一个结论误导教师。
 */
public record ExamAnalyticsResponse(
        Long examId,
        String title,
        Integer totalScore,
        Integer passScore,
        /** 参与作答（含未交卷）的人数 */
        Integer participantCount,
        /** 已交卷人数，统计口径以已交卷为准 */
        Integer submittedCount,
        /** 样本是否充足（已交卷人数 >= 30） */
        Boolean sufficient,
        Double avgScore,
        Double avgRate,
        Integer maxScore,
        Integer minScore,
        Double passRate,
        List<ScoreBucket> scoreDistribution,
        List<QuestionStat> questionStats,
        List<KnowledgeStat> knowledgeStats,
        List<StudentRow> students,
        /** 当前筛选的班级；为空表示全部班级 */
        Long classId,
        String className) {

    /** 判断类结论的样本门槛：低于该人数时统计量波动大，不足以支撑结论 */
    public static final int MIN_SAMPLE = 30;

    public static final String MIN_SAMPLE_NOTE =
            "已交卷人数少于 " + MIN_SAMPLE + " 人，统计量波动较大，结论仅供参考";

    /** 分数段分布的一个区间 */
    public record ScoreBucket(String label, int count, double percent) {
    }

    /**
     * 单题统计。
     * <p>
     * judgementCode 便于前端做颜色与图标，可选值：
     * GOOD（贴合）/ TOO_HARD（偏难）/ TOO_EASY（偏易）/
     * HARD_UNCLEAR（偏难且耗时短，可能表述不清）/ INSUFFICIENT（样本不足）。
     */
    public record QuestionStat(
            Long questionId,
            int order,
            String typeLabel,
            String stem,
            Integer score,
            int answerCount,
            int correctCount,
            Double correctRate,
            Double avgTimeSpentSec,
            Double avgSubmitCount,
            String judgement,
            String judgementCode) {
    }

    /** 知识点掌握度（全班口径） */
    public record KnowledgeStat(
            Long knowledgePointId,
            String name,
            int questionCount,
            int answerCount,
            int correctCount,
            Integer possibleScore,
            Integer earnedScore,
            Double mastery,
            Boolean weak) {
    }

    /** 成绩表中的一行 */
    public record StudentRow(
            Long recordId,
            String studentName,
            String username,
            String className,
            Integer score,
            Double rate,
            Boolean passed,
            String statusLabel,
            Boolean autoSubmitted,
            Integer switchScreenCount,
            String submitTime) {
    }
}
