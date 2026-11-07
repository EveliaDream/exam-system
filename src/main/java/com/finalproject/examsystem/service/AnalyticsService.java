package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.domain.Answer;
import com.finalproject.examsystem.domain.Exam;
import com.finalproject.examsystem.domain.ExamQuestion;
import com.finalproject.examsystem.domain.ExamRecord;
import com.finalproject.examsystem.domain.KnowledgePoint;
import com.finalproject.examsystem.domain.RecordStatus;
import com.finalproject.examsystem.dto.analytics.ExamAnalyticsResponse;
import com.finalproject.examsystem.dto.analytics.MyKnowledgeAnalysisResponse;
import com.finalproject.examsystem.dto.analytics.RecordDetailResponse;
import com.finalproject.examsystem.repository.AnswerRepository;
import com.finalproject.examsystem.repository.ExamQuestionRepository;
import com.finalproject.examsystem.repository.ExamRecordRepository;
import com.finalproject.examsystem.repository.ExamRepository;
import com.finalproject.examsystem.repository.QuestionKnowledgeRepository;
import com.finalproject.examsystem.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情分析。
 * <p>
 * 本类是「学情数据驱动」定位的落地实现，只有三个产出：
 * <ol>
 *   <li><b>学生视角</b>：知识点掌握度 + 薄弱知识点推送（考完告诉学生该补什么）</li>
 *   <li><b>教师视角</b>：成绩统计 + 题目难度与班级水平的贴合度 + 知识点掌握度（考完告诉教师该讲什么）</li>
 *   <li><b>可解释性</b>：每个判断都给出依据（得分率、耗时），样本不足时明确标注"仅供参考"</li>
 * </ol>
 * 掌握度按**分值加权**而非题数平均：同一知识点下 5 分题与 10 分题权重不同，
 * 按题数平均会失真。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 判定为「明显偏难 / 偏易」的偏离阈值（相对班级平均得分率） */
    private static final double DEVIATION = 0.25;

    /** 掌握度低于该值视为薄弱 */
    private static final double WEAK_THRESHOLD = MyKnowledgeAnalysisResponse.WEAK_THRESHOLD;

    private final ExamRepository examRepository;
    private final ExamRecordRepository examRecordRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionKnowledgeRepository questionKnowledgeRepository;
    private final com.finalproject.examsystem.repository.StudentClassRepository studentClassRepository;

    private static final List<RecordStatus> SUBMITTED =
            List.of(RecordStatus.SUBMITTED, RecordStatus.AUTO_SUBMITTED);

    // ==================== 教师端：考试分析 ====================

    /**
     * 一场考试的学情分析。
     * <p>
     * 数据范围：只统计**已交卷**的记录。未交卷的作答不完整，纳入平均分会把成绩拉低，
     * 反而失真——教师看到"平均分 40"时应当能确定这 40 是真实作答的结果。
     * <p>
     * classId 非空时只统计该班学生：同一次考试可能发布给多个班，教师往往只关心某一个班的学情。
     */
    @Transactional(readOnly = true)
    public ExamAnalyticsResponse analyzeExam(Long examId, Long classId) {
        Exam exam = examRepository.findByIdAndDeletedFalse(examId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EXAM_NOT_FOUND));
        if (exam.getCreatedBy() != null
                && !exam.getCreatedBy().getId().equals(SecurityUtils.currentUserId())) {
            throw BusinessException.of(ErrorCode.EXAM_NOT_OWNED);
        }

        List<ExamRecord> examRecords = examRecordRepository.findByExamId(examId);
        if (classId != null) {
            // 班级过滤放在内存里做而不是写进 JPQL：单场考试的记录数最多一个班的人数，
            // 而多一个查询分支就多一处可能不一致的统计口径
            examRecords = examRecords.stream()
                    .filter(r -> inClass(r, classId))
                    .toList();
        }
        List<ExamRecord> records = examRecords.stream()
                .filter(r -> r.getStatus() != null && SUBMITTED.contains(r.getStatus()))
                .toList();
        int participantCount = examRecords.size();
        String className = classId == null ? null : resolveClassName(classId);
        List<ExamQuestion> snapshots =
                examQuestionRepository.findByExamIdAndAttemptNoOrderBySortOrderAsc(examId, 1);
        Map<Long, ExamQuestion> eqById = new LinkedHashMap<>();
        snapshots.forEach(eq -> eqById.put(eq.getQuestionId(), eq));

        List<Answer> allAnswers = records.isEmpty() ? List.of()
                : answerRepository.findByRecordIdIn(records.stream().map(ExamRecord::getId).toList());
        Map<Long, List<Answer>> answersByRecord = new HashMap<>();
        allAnswers.forEach(a -> answersByRecord
                .computeIfAbsent(a.getRecord().getId(), k -> new ArrayList<>()).add(a));

        Map<Long, List<KnowledgePoint>> kpByQuestion = loadKnowledge(
                snapshots.stream().map(ExamQuestion::getQuestionId).toList());

        boolean sufficient = records.size() >= ExamAnalyticsResponse.MIN_SAMPLE;
        double avgScore = records.stream().mapToInt(this::scoreOf).average().orElse(0);
        int total = exam.getTotalScore() == null ? 0 : exam.getTotalScore();
        double avgRate = total == 0 ? 0 : avgScore / total;

        int maxScore = records.stream().mapToInt(this::scoreOf).max().orElse(0);
        int minScore = records.stream().mapToInt(this::scoreOf).min().orElse(0);
        long passed = records.stream().filter(r -> isPassed(r, exam)).count();
        double passRate = records.isEmpty() ? 0 : (double) passed / records.size();

        return new ExamAnalyticsResponse(
                exam.getId(),
                exam.getTitle(),
                exam.getTotalScore(),
                exam.getPassScore(),
                // 参与人数 = 已产生记录的人数（含未交卷），用于提示"还有人没交卷"
                participantCount,
                records.size(),
                sufficient,
                round(avgScore),
                round(avgRate),
                maxScore,
                minScore,
                round(passRate),
                buildDistribution(records, exam),
                buildQuestionStats(snapshots, allAnswers, avgRate, sufficient),
                buildKnowledgeStats(snapshots, allAnswers, kpByQuestion),
                buildStudentRows(records),
                classId,
                className);
    }

    /** 不按班级过滤时的重载，供内部调用 */
    @Transactional(readOnly = true)
    public ExamAnalyticsResponse analyzeExam(Long examId) {
        return analyzeExam(examId, null);
    }

    private boolean inClass(ExamRecord r, Long classId) {
        return r.getStudent() != null
                && r.getStudent().getStudentClass() != null
                && classId.equals(r.getStudent().getStudentClass().getId());
    }

    private String resolveClassName(Long classId) {
        return studentClassRepository.findById(classId)
                .map(c -> c.getName())
                .orElse("未知班级");
    }

    // ==================== 学生端：知识点分析 ====================

    /**
     * 我的学情分析：知识点掌握度，按掌握度升序推送薄弱知识点。
     * <p>
     * 推送规则：掌握度 < 0.6 的取前 5 个；若全部达到 0.6，则返回空列表
     * （不用"取最低的几个"硬凑——没有薄弱点就该明确告诉学生没有）。
     */
    @Transactional(readOnly = true)
    public MyKnowledgeAnalysisResponse myAnalysis(Long recordId) {
        ExamRecord record = requireMyRecord(recordId);
        if (!record.finished()) {
            throw BusinessException.of(ErrorCode.RECORD_NOT_FOUND, "尚未交卷，暂无学情分析");
        }
        Exam exam = record.getExam();
        List<ExamQuestion> snapshots =
                examQuestionRepository.findByExamIdAndAttemptNoOrderBySortOrderAsc(exam.getId(), 1);
        Map<Long, ExamQuestion> eqById = new HashMap<>();
        snapshots.forEach(eq -> eqById.put(eq.getQuestionId(), eq));
        Map<Long, List<KnowledgePoint>> kpByQuestion = loadKnowledge(
                snapshots.stream().map(ExamQuestion::getQuestionId).toList());

        List<Answer> answers = answerRepository.findByRecordId(recordId);

        // 按知识点聚合：分值加权
        Map<Long, Agg> aggMap = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (Answer a : answers) {
            ExamQuestion eq = eqById.get(a.getQuestionId());
            if (eq == null) {
                continue;
            }
            for (KnowledgePoint kp : kpByQuestion.getOrDefault(a.getQuestionId(), List.of())) {
                names.put(kp.getId(), kp.getName());
                Agg agg = aggMap.computeIfAbsent(kp.getId(), k -> new Agg());
                agg.possible += eq.getScore();
                agg.earned += a.getScore() == null ? 0 : a.getScore();
                agg.questionCount++;
                if (Boolean.TRUE.equals(a.getCorrect())) {
                    agg.correct++;
                } else {
                    agg.wrong++;
                }
            }
        }

        List<MyKnowledgeAnalysisResponse.KnowledgeMastery> all = aggMap.entrySet().stream()
                .map(e -> {
                    Agg g = e.getValue();
                    double mastery = g.possible == 0 ? 0 : (double) g.earned / g.possible;
                    return new MyKnowledgeAnalysisResponse.KnowledgeMastery(
                            e.getKey(),
                            names.getOrDefault(e.getKey(), "未知知识点"),
                            round(mastery),
                            g.correct,
                            g.wrong,
                            g.questionCount);
                })
                .sorted(Comparator.comparingDouble(MyKnowledgeAnalysisResponse.KnowledgeMastery::mastery))
                .toList();

        List<MyKnowledgeAnalysisResponse.KnowledgeMastery> weak = all.stream()
                .filter(k -> k.mastery() < WEAK_THRESHOLD)
                .limit(MyKnowledgeAnalysisResponse.MAX_WEAK_POINTS)
                .toList();

        int score = scoreOf(record);
        return new MyKnowledgeAnalysisResponse(
                record.getId(),
                exam.getTitle(),
                score,
                exam.getTotalScore(),
                exam.getPassScore(),
                isPassed(record, exam),
                weak,
                all,
                !all.isEmpty());
    }

    // ==================== 学生端：逐题作答详情 ====================

    /** 交卷后查看逐题详情（含正确答案与解析） */
    @Transactional(readOnly = true)
    public RecordDetailResponse recordDetail(Long recordId) {
        ExamRecord record = requireMyRecord(recordId);
        if (!record.finished()) {
            throw BusinessException.of(ErrorCode.RECORD_NOT_FOUND, "尚未交卷，不能查看作答详情");
        }
        Exam exam = record.getExam();
        List<ExamQuestion> snapshots =
                examQuestionRepository.findByExamIdAndAttemptNoOrderBySortOrderAsc(exam.getId(), 1);
        Map<Long, Answer> answerByQuestion = new HashMap<>();
        answerRepository.findByRecordId(recordId)
                .forEach(a -> answerByQuestion.put(a.getQuestionId(), a));

        List<RecordDetailResponse.AnsweredQuestion> questions = new ArrayList<>();
        int order = 0;
        for (ExamQuestion eq : snapshots) {
            order++;
            Answer a = answerByQuestion.get(eq.getQuestionId());
            questions.add(new RecordDetailResponse.AnsweredQuestion(
                    eq.getQuestionId(),
                    order,
                    eq.getType() == null ? null : eq.getType().getLabel(),
                    eq.getStem(),
                    eq.getOptions() == null ? List.of() : eq.getOptions(),
                    eq.getImageUrl(),
                    eq.getAnswer() == null ? List.of() : eq.getAnswer(),
                    a == null || a.getStudentAnswer() == null ? List.of() : a.getStudentAnswer(),
                    a == null ? 0 : (a.getScore() == null ? 0 : a.getScore()),
                    eq.getScore(),
                    a != null && Boolean.TRUE.equals(a.getCorrect()),
                    a == null ? null : a.getTimeSpentSec(),
                    a == null ? null : a.getSubmitCount(),
                    eq.getAnalysis()));
        }

        int score = scoreOf(record);
        return new RecordDetailResponse(
                record.getId(),
                exam.getId(),
                exam.getTitle(),
                score,
                exam.getTotalScore(),
                exam.getPassScore(),
                isPassed(record, exam),
                questions);
    }

    // ==================== 内部计算 ====================

    private List<ExamAnalyticsResponse.QuestionStat> buildQuestionStats(
            List<ExamQuestion> snapshots, List<Answer> allAnswers,
            double classRate, boolean sufficient) {

        Map<Long, List<Answer>> byQuestion = new HashMap<>();
        allAnswers.forEach(a -> byQuestion
                .computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a));

        // 各题平均耗时，用于「偏难但耗时短」的交叉判断
        double medianTime = median(allAnswers.stream()
                .map(Answer::getTimeSpentSec)
                .filter(java.util.Objects::nonNull)
                .map(Integer::doubleValue)
                .toList());

        List<ExamAnalyticsResponse.QuestionStat> stats = new ArrayList<>();
        int order = 0;
        for (ExamQuestion eq : snapshots) {
            order++;
            List<Answer> list = byQuestion.getOrDefault(eq.getQuestionId(), List.of());
            int answerCount = list.size();
            int correctCount = (int) list.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getCorrect())).count();
            int possible = eq.getScore() * answerCount;
            int earned = list.stream().mapToInt(a -> a.getScore() == null ? 0 : a.getScore()).sum();
            double rate = possible == 0 ? 0 : (double) earned / possible;
            double avgTime = average(list.stream()
                    .map(Answer::getTimeSpentSec)
                    .filter(java.util.Objects::nonNull)
                    .map(Integer::doubleValue).toList());
            double avgSubmit = average(list.stream()
                    .map(Answer::getSubmitCount)
                    .filter(java.util.Objects::nonNull)
                    .map(Integer::doubleValue).toList());

            String code;
            String judgement;
            if (!sufficient) {
                code = "INSUFFICIENT";
                judgement = "样本不足，仅供参考";
            } else if (rate <= classRate - DEVIATION) {
                // 交叉判断：偏难但耗时明显低于中位数，说明学生很快就放弃 / 题干不清
                boolean tooFast = medianTime > 0 && avgTime > 0 && avgTime < medianTime * 0.5;
                if (tooFast) {
                    code = "HARD_UNCLEAR";
                    judgement = "偏难且作答耗时明显偏短，建议复核题干表述";
                } else {
                    code = "TOO_HARD";
                    judgement = "明显偏难，建议复核知识点归属或降低难度";
                }
            } else if (rate >= classRate + DEVIATION) {
                code = "TOO_EASY";
                judgement = "明显偏易，考查价值有限，建议替换";
            } else {
                code = "GOOD";
                judgement = "与班级水平贴合";
            }

            stats.add(new ExamAnalyticsResponse.QuestionStat(
                    eq.getQuestionId(), order,
                    eq.getType() == null ? null : eq.getType().getLabel(),
                    eq.getStem(), eq.getScore(),
                    answerCount, correctCount, round(rate),
                    round(avgTime), round(avgSubmit),
                    judgement, code));
        }
        return stats;
    }

    private List<ExamAnalyticsResponse.KnowledgeStat> buildKnowledgeStats(
            List<ExamQuestion> snapshots, List<Answer> allAnswers,
            Map<Long, List<KnowledgePoint>> kpByQuestion) {

        Map<Long, ExamQuestion> eqById = new HashMap<>();
        snapshots.forEach(eq -> eqById.put(eq.getQuestionId(), eq));

        Map<Long, Agg> aggMap = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        Map<Long, java.util.Set<Long>> questionsOfKp = new HashMap<>();

        for (Answer a : allAnswers) {
            ExamQuestion eq = eqById.get(a.getQuestionId());
            if (eq == null) {
                continue;
            }
            for (KnowledgePoint kp : kpByQuestion.getOrDefault(a.getQuestionId(), List.of())) {
                names.put(kp.getId(), kp.getName());
                questionsOfKp.computeIfAbsent(kp.getId(), k -> new java.util.HashSet<>())
                        .add(eq.getQuestionId());
                Agg agg = aggMap.computeIfAbsent(kp.getId(), k -> new Agg());
                agg.possible += eq.getScore();
                agg.earned += a.getScore() == null ? 0 : a.getScore();
                agg.questionCount++;
                if (Boolean.TRUE.equals(a.getCorrect())) {
                    agg.correct++;
                } else {
                    agg.wrong++;
                }
            }
        }

        return aggMap.entrySet().stream()
                .map(e -> {
                    Agg g = e.getValue();
                    double mastery = g.possible == 0 ? 0 : (double) g.earned / g.possible;
                    return new ExamAnalyticsResponse.KnowledgeStat(
                            e.getKey(),
                            names.getOrDefault(e.getKey(), "未知知识点"),
                            questionsOfKp.getOrDefault(e.getKey(), java.util.Set.of()).size(),
                            g.questionCount,
                            g.correct,
                            g.possible,
                            g.earned,
                            round(mastery),
                            mastery < WEAK_THRESHOLD);
                })
                .sorted(Comparator.comparingDouble(ExamAnalyticsResponse.KnowledgeStat::mastery))
                .toList();
    }

    private List<ExamAnalyticsResponse.StudentRow> buildStudentRows(List<ExamRecord> records) {
        List<ExamAnalyticsResponse.StudentRow> rows = new ArrayList<>();
        for (ExamRecord r : records) {
            Exam exam = r.getExam();
            int score = scoreOf(r);
            int total = exam.getTotalScore() == null ? 0 : exam.getTotalScore();
            rows.add(new ExamAnalyticsResponse.StudentRow(
                    r.getId(),
                    r.getStudent() == null ? "—" : r.getStudent().getRealName(),
                    r.getStudent() == null ? "—" : r.getStudent().getUsername(),
                    r.getStudent() == null || r.getStudent().getStudentClass() == null
                            ? "—" : r.getStudent().getStudentClass().getName(),
                    score,
                    total == 0 ? 0 : round((double) score / total),
                    isPassed(r, exam),
                    r.getStatus() == null ? null : r.getStatus().getLabel(),
                    r.getStatus() == RecordStatus.AUTO_SUBMITTED,
                    r.getSwitchScreenCount(),
                    r.getSubmitTime() == null ? null : r.getSubmitTime().format(FMT)));
        }
        // 按分数降序，便于教师看排名
        rows.sort(Comparator.comparing(ExamAnalyticsResponse.StudentRow::score).reversed());
        return rows;
    }

    /** 分数段分布按**得分率**划分，便于不同总分的考试横向对比 */
    private List<ExamAnalyticsResponse.ScoreBucket> buildDistribution(List<ExamRecord> records, Exam exam) {
        int total = exam.getTotalScore() == null ? 0 : exam.getTotalScore();
        int[] counts = new int[5];
        for (ExamRecord r : records) {
            double rate = total == 0 ? 0 : (double) scoreOf(r) / total;
            int idx = rate < 0.6 ? 0 : rate < 0.7 ? 1 : rate < 0.8 ? 2 : rate < 0.9 ? 3 : 4;
            counts[idx]++;
        }
        String[] labels = {"低于 60%", "60%~69%", "70%~79%", "80%~89%", "90%~100%"};
        List<ExamAnalyticsResponse.ScoreBucket> buckets = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            double percent = records.isEmpty() ? 0 : (double) counts[i] / records.size();
            buckets.add(new ExamAnalyticsResponse.ScoreBucket(labels[i], counts[i], round(percent)));
        }
        return buckets;
    }

    /** 批量取题目 → 知识点映射（questionId 可能重复，按题聚合） */
    private Map<Long, List<KnowledgePoint>> loadKnowledge(List<Long> questionIds) {
        Map<Long, List<KnowledgePoint>> map = new HashMap<>();
        if (questionIds.isEmpty()) {
            return map;
        }
        for (Object[] row : questionKnowledgeRepository.findKnowledgeByQuestionIds(questionIds)) {
            Long qid = (Long) row[0];
            map.computeIfAbsent(qid, k -> new ArrayList<>()).add((KnowledgePoint) row[1]);
        }
        return map;
    }

    private ExamRecord requireMyRecord(Long recordId) {
        return examRecordRepository.findByIdAndStudentId(recordId, SecurityUtils.currentUserId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.RECORD_NOT_FOUND));
    }

    private int scoreOf(ExamRecord r) {
        return r.getObjectiveScore() == null ? 0 : r.getObjectiveScore();
    }

    private boolean isPassed(ExamRecord r, Exam exam) {
        Integer pass = exam.getPassScore();
        return pass != null && scoreOf(r) >= pass;
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    private double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    /** 知识点聚合的中间累加器 */
    private static final class Agg {
        int possible;
        int earned;
        int questionCount;
        int correct;
        int wrong;
    }
}
