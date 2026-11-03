package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.domain.Answer;
import com.finalproject.examsystem.domain.Exam;
import com.finalproject.examsystem.domain.ExamQuestion;
import com.finalproject.examsystem.domain.ExamRecord;
import com.finalproject.examsystem.domain.ExamStatus;
import com.finalproject.examsystem.domain.RecordStatus;
import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.dto.answer.ExamPaperResponse;
import com.finalproject.examsystem.dto.answer.MyRecordResponse;
import com.finalproject.examsystem.dto.answer.PaperQuestionResponse;
import com.finalproject.examsystem.dto.answer.SavedAnswerView;
import com.finalproject.examsystem.dto.answer.SaveAnswersRequest;
import com.finalproject.examsystem.dto.answer.SubmitResultResponse;
import com.finalproject.examsystem.repository.AnswerRepository;
import com.finalproject.examsystem.repository.ExamClassRepository;
import com.finalproject.examsystem.repository.ExamQuestionRepository;
import com.finalproject.examsystem.repository.ExamRecordRepository;
import com.finalproject.examsystem.repository.ExamRepository;
import com.finalproject.examsystem.repository.UserRepository;
import com.finalproject.examsystem.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 在线作答与自动判分。
 * <p>
 * 五条核心约束：
 * <ol>
 *   <li><b>题面不含答案</b>：学生端 DTO 单独定义，永不返回 answer / analysis</li>
 *   <li><b>权威计时在服务端</b>：截止时间 = min(开始作答 + 时长, 考试结束时间)，
 *       每次保存与交卷都重新校验；前端倒计时只用于显示</li>
 *   <li><b>进入即预填</b>：按试卷快照预填 answer 行，自动保存只更新这些行，
 *       因此断线重进能恢复已答内容，且不需要缓存中间件</li>
 *   <li><b>判分只读快照</b>：不查题库，教师后来改题不影响历史作答</li>
 *   <li><b>交卷幂等</b>：重复提交返回同一结果，不会重复计分</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamTakingService {

    private static final int CURRENT_ATTEMPT = 1;

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamClassRepository examClassRepository;
    private final ExamRecordRepository examRecordRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;

    // ==================== 进入考试 / 继续作答 ====================

    /**
     * 开始或继续作答。
     * <p>
     * 三种情况：
     * <ul>
     *   <li>还没作答过 → 创建记录、预填答题行、返回试卷</li>
     *   <li>有进行中的记录 → 返回已有试卷与已保存答案（断线重进）</li>
     *   <li>已交卷 / 已超时 → 返回 status 为已结束的空试卷，由前端跳转成绩页</li>
     * </ul>
     * 用「返回空试卷 + status」而不是抛异常，是为了让前端能用同一套渲染逻辑处理，
     * 不必在错误分支里再解析错误码。
     */
    @Transactional
    public ExamPaperResponse start(Long examId) {
        User student = currentUser();
        if (student.getStudentClass() == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "你还没有被分配到班级，无法参加考试");
        }
        Exam exam = requireVisibleExam(examId, student.getStudentClass().getId());

        ExamRecord record = examRecordRepository
                .findTopByExamIdAndStudentIdOrderByAttemptNoDesc(examId, student.getId())
                .orElse(null);

        if (record == null) {
            record = createRecord(exam, student);
        } else if (record.finished()) {
            // 已完成：返回已结束的试卷壳，前端据此跳到成绩页
            return buildPaper(exam, record, List.of(), List.of());
        }

        // 进行中但已超时：先自动交卷，再把状态回给前端
        if (isExpired(record, exam)) {
            doGrade(exam, record, true);
            log.info("记录 {} 超时，已自动交卷", record.getId());
            return buildPaper(exam, record, List.of(), List.of());
        }

        List<ExamQuestion> snapshots = loadSnapshots(examId);
        List<Answer> answers = answerRepository.findByRecordId(record.getId());
        List<PaperQuestionResponse> questions = snapshots.stream()
                .map(PaperQuestionResponse::from)
                .toList();
        List<SavedAnswerView> saved = answers.stream()
                .filter(Answer::answered)
                .map(a -> new SavedAnswerView(a.getQuestionId(), a.getStudentAnswer()))
                .toList();
        return buildPaper(exam, record, questions, saved);
    }

    /** 继续作答：与 start 等价，单独暴露一个只读接口供刷新页面时调用 */
    @Transactional
    public ExamPaperResponse paper(Long examId) {
        return start(examId);
    }

    // ==================== 自动保存 ====================

    /**
     * 自动保存本次变更的答案。
     * <p>
     * 只更新请求里出现的题目；同时记录该题的作答耗时与修改次数（阶段 6 学情分析的输入）。
     * 若此时已超时，则自动交卷并把 submitted 置为 true，前端收到后跳转成绩页。
     */
    @Transactional
    public SaveAck saveAnswers(Long recordId, SaveAnswersRequest request) {
        ExamRecord record = requireOwnedRecord(recordId);
        if (record.finished()) {
            return new SaveAck(LocalDateTime.now(), 0L, true, record.getId());
        }

        Exam exam = record.getExam();
        if (isExpired(record, exam)) {
            doGrade(exam, record, true);
            return new SaveAck(LocalDateTime.now(), 0L, true, record.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        // 单题耗时上限：不能超过本次作答的总时长，防止前端异常值污染学情统计
        long maxSpent = exam.getDuration() * 60L;

        for (SaveAnswersRequest.Item item : request.answers()) {
            Answer answer = answerRepository
                    .findByRecordIdAndQuestionId(recordId, item.questionId())
                    .orElse(null);
            if (answer == null) {
                // 不属于本试卷的题目，直接忽略（不报错，避免一次脏请求打断整次自动保存）
                continue;
            }
            List<String> incoming = item.answer() == null ? List.of() : item.answer();

            if (answer.getFirstAnsweredAt() == null && incoming.stream().anyMatch(s -> s != null && !s.isBlank())) {
                answer.setFirstAnsweredAt(now);
            }
            answer.setStudentAnswer(incoming);
            answer.setAnsweredAt(now);

            if (item.timeSpentSec() != null) {
                answer.setTimeSpentSec((int) Math.min(item.timeSpentSec(), maxSpent));
            }
            if (item.submitCount() != null) {
                answer.setSubmitCount(item.submitCount());
            }
        }
        // findByRecordIdAndQuestionId 返回的实体处于事务上下文中，脏检查会统一落库
        return new SaveAck(now, remainingSeconds(record, exam), false, record.getId());
    }

    /** 自动保存的轻量回执 */
    public record SaveAck(LocalDateTime savedAt, Long remainingSeconds, Boolean submitted, Long recordId) {
    }

    // ==================== 交卷与判分 ====================

    /**
     * 交卷并自动判分。
     * <p>
     * 幂等：已交卷时直接返回既有结果，不会重复累加分数。
     * 超时由服务端判定，此时以 AUTO_SUBMITTED 落库，与主动交卷区分开。
     */
    @Transactional
    public SubmitResultResponse submit(Long recordId) {
        ExamRecord record = requireOwnedRecord(recordId);
        Exam exam = record.getExam();

        if (record.finished()) {
            return buildResult(record, exam);
        }
        boolean auto = isExpired(record, exam);
        doGrade(exam, record, auto);
        log.info("记录 {} 交卷（{}），得分 {}", recordId,
                auto ? "超时自动" : "学生主动", record.getObjectiveScore());
        return buildResult(record, exam);
    }

    /** 查询成绩：只能查自己的记录 */
    @Transactional(readOnly = true)
    public SubmitResultResponse result(Long recordId) {
        ExamRecord record = requireOwnedRecord(recordId);
        if (!record.finished()) {
            throw BusinessException.of(ErrorCode.RECORD_NOT_FOUND, "该次作答尚未交卷");
        }
        return buildResult(record, record.getExam());
    }

    /** 我的成绩列表 */
    @Transactional(readOnly = true)
    public List<MyRecordResponse> myRecords() {
        Long studentId = SecurityUtils.currentUserId();
        return examRecordRepository
                .findByStudentIdAndStatusInOrderByIdDesc(studentId,
                        List.of(RecordStatus.SUBMITTED, RecordStatus.AUTO_SUBMITTED))
                .stream()
                .map(r -> {
                    Exam e = r.getExam();
                    int score = r.getObjectiveScore() == null ? 0 : r.getObjectiveScore();
                    return new MyRecordResponse(
                            r.getId(), e.getId(), e.getTitle(),
                            e.getCourse() == null ? null : e.getCourse().getName(),
                            score, e.getTotalScore(), e.getPassScore(),
                            e.getPassScore() != null && score >= e.getPassScore(),
                            r.getStatus(), r.getStatus().getLabel(),
                            r.getAttemptNo(), r.getStartTime(), r.getSubmitTime());
                })
                .toList();
    }

    // ==================== 切屏上报 ====================

    /**
     * 切屏次数上报。
     * <p>
     * 只做留痕，供教师参考，不自动判作弊——浏览器端信号不可靠，
     * 误判会直接影响学生成绩（详见阶段 5 设计说明）。
     */
    @Transactional
    public int reportSwitchScreen(Long recordId) {
        ExamRecord record = requireOwnedRecord(recordId);
        if (record.finished()) {
            return record.getSwitchScreenCount();
        }
        record.setSwitchScreenCount(record.getSwitchScreenCount() + 1);
        examRecordRepository.save(record);
        return record.getSwitchScreenCount();
    }

    // ==================== 内部方法 ====================

    /** 判分：只读组卷快照，逐题计算得分并汇总 */
    private SubmitResultResponse doGrade(Exam exam, ExamRecord record, boolean auto) {
        List<ExamQuestion> snapshots = loadSnapshots(exam.getId());
        Map<Long, ExamQuestion> byId = new HashMap<>();
        snapshots.forEach(eq -> byId.put(eq.getQuestionId(), eq));

        List<Answer> answers = answerRepository.findByRecordId(record.getId());
        int total = 0;
        int correct = 0;
        for (Answer a : answers) {
            ExamQuestion eq = byId.get(a.getQuestionId());
            if (eq == null) {
                // 快照已不存在的异常数据：按 0 分处理，不影响其余题目判分
                a.setScore(0);
                a.setCorrect(false);
                continue;
            }
            boolean ok = ObjectiveGrader.isCorrect(eq.getType(), eq.getAnswer(), a.getStudentAnswer());
            a.setCorrect(ok);
            a.setScore(ok ? eq.getScore() : 0);
            total += a.getScore();
            if (ok) {
                correct++;
            }
        }
        answerRepository.saveAll(answers);

        record.setObjectiveScore(total);
        record.setStatus(auto ? RecordStatus.AUTO_SUBMITTED : RecordStatus.SUBMITTED);
        record.setSubmitTime(LocalDateTime.now());
        examRecordRepository.save(record);

        return SubmitResultResponse.from(record, exam.getTitle(), correct, answers.size(), auto);
    }

    private SubmitResultResponse buildResult(ExamRecord record, Exam exam) {
        long correct = answerRepository.countByRecordIdAndCorrectTrue(record.getId());
        long total = answerRepository.countByRecordId(record.getId());
        return SubmitResultResponse.from(record, exam.getTitle(),
                (int) correct, (int) total,
                record.getStatus() == RecordStatus.AUTO_SUBMITTED);
    }

    private ExamRecord createRecord(Exam exam, User student) {
        ExamRecord record = new ExamRecord();
        record.setExam(exam);
        record.setStudent(student);
        record.setAttemptNo(CURRENT_ATTEMPT);
        record.setStartTime(LocalDateTime.now());
        record.setStatus(RecordStatus.IN_PROGRESS);
        record.setSwitchScreenCount(0);
        examRecordRepository.save(record);

        // 按试卷快照预填答题行：断线重进时直接读这些行即可恢复
        List<ExamQuestion> snapshots = loadSnapshots(exam.getId());
        List<Answer> rows = snapshots.stream()
                .map(eq -> Answer.empty(record, eq.getQuestionId()))
                .toList();
        answerRepository.saveAll(rows);

        log.info("学生 {} 开始考试 {}，预填 {} 题", student.getId(), exam.getId(), rows.size());
        return record;
    }

    private ExamPaperResponse buildPaper(Exam exam, ExamRecord record,
                                         List<PaperQuestionResponse> questions,
                                         List<SavedAnswerView> saved) {
        LocalDateTime deadline = exam.deadlineOf(record.getStartTime());
        // 已交卷的记录剩余时间必须为 0：否则前端在结束态还会显示倒计时，
        // 学生可能误以为仍在作答（这个缺陷是测试 startAfterSubmitReturnsFinishedShell 抓出来的）。
        long remaining = record.finished() ? 0L : remainingSeconds(record, exam);
        return new ExamPaperResponse(
                record.getId(),
                exam.getId(),
                exam.getTitle(),
                exam.getDescription(),
                exam.getTotalScore(),
                exam.getPassScore(),
                exam.getDuration(),
                record.getStartTime(),
                deadline,
                remaining,
                exam.getShuffleQuestion(),
                exam.getShuffleOption(),
                record.getAttemptNo(),
                record.getStatus(),
                record.getSwitchScreenCount(),
                questions,
                saved);
    }

    private List<ExamQuestion> loadSnapshots(Long examId) {
        return examQuestionRepository
                .findByExamIdAndAttemptNoOrderBySortOrderAsc(examId, CURRENT_ATTEMPT);
    }

    private boolean isExpired(ExamRecord record, Exam exam) {
        LocalDateTime deadline = exam.deadlineOf(record.getStartTime());
        return !LocalDateTime.now().isBefore(deadline);
    }

    private Long remainingSeconds(ExamRecord record, Exam exam) {
        LocalDateTime deadline = exam.deadlineOf(record.getStartTime());
        long seconds = Duration.between(LocalDateTime.now(), deadline).getSeconds();
        return Math.max(0, seconds);
    }

    /** 考试必须存在、已发布、且发布给了该学生所在班级 */
    private Exam requireVisibleExam(Long examId, Long classId) {
        Exam exam = examRepository.findByIdAndDeletedFalse(examId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EXAM_NOT_FOUND));
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw BusinessException.of(ErrorCode.EXAM_STATUS_INVALID,
                    exam.getStatus() == ExamStatus.DRAFT ? "考试尚未发布" : "考试已结束");
        }
        boolean visible = examClassRepository.findByExamId(examId).stream()
                .anyMatch(ec -> Objects.equals(ec.getClassId(), classId));
        if (!visible) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "这场考试没有发布给你所在的班级");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            throw BusinessException.of(ErrorCode.EXAM_NOT_STARTED);
        }
        if (now.isAfter(exam.getEndTime())) {
            throw BusinessException.of(ErrorCode.EXAM_ENDED);
        }
        return exam;
    }

    /** 记录必须属于当前学生：防止改 recordId 查看/提交别人的卷子 */
    private ExamRecord requireOwnedRecord(Long recordId) {
        Long studentId = SecurityUtils.currentUserId();
        return examRecordRepository.findByIdAndStudentId(recordId, studentId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RECORD_NOT_FOUND));
    }

    private User currentUser() {
        return userRepository.findByIdAndDeletedFalse(SecurityUtils.currentUserId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
    }
}
