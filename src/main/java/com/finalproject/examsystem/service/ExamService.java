package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.domain.Course;
import com.finalproject.examsystem.domain.Exam;
import com.finalproject.examsystem.domain.ExamClass;
import com.finalproject.examsystem.domain.ExamPhase;
import com.finalproject.examsystem.domain.ExamQuestion;
import com.finalproject.examsystem.domain.ExamStatus;
import com.finalproject.examsystem.domain.KnowledgePoint;
import com.finalproject.examsystem.domain.Question;
import com.finalproject.examsystem.domain.Role;
import com.finalproject.examsystem.domain.StudentClass;
import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.dto.exam.ClassBindRequest;
import com.finalproject.examsystem.dto.exam.ExamDetailResponse;
import com.finalproject.examsystem.dto.exam.ExamQuery;
import com.finalproject.examsystem.dto.exam.ExamQuestionView;
import com.finalproject.examsystem.dto.exam.ExamQuestionsRequest;
import com.finalproject.examsystem.dto.exam.ExamResponse;
import com.finalproject.examsystem.dto.exam.ExamSaveRequest;
import com.finalproject.examsystem.dto.exam.ExamScheduleRequest;
import com.finalproject.examsystem.dto.exam.RandomAssembleRequest;
import com.finalproject.examsystem.dto.exam.StudentExamResponse;
import com.finalproject.examsystem.repository.CourseRepository;
import com.finalproject.examsystem.repository.ExamClassRepository;
import com.finalproject.examsystem.repository.ExamQuestionRepository;
import com.finalproject.examsystem.repository.ExamRecordRepository;
import com.finalproject.examsystem.repository.ExamRepository;
import com.finalproject.examsystem.repository.KnowledgePointRepository;
import com.finalproject.examsystem.repository.QuestionKnowledgeRepository;
import com.finalproject.examsystem.repository.QuestionRepository;
import com.finalproject.examsystem.repository.StudentClassRepository;
import com.finalproject.examsystem.repository.UserRepository;
import com.finalproject.examsystem.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组卷与考试发布。
 * <p>
 * 核心设计约束（答辩重点）：
 * <ol>
 *   <li><b>组卷即快照</b>：把题干、选项、正确答案、解析、分值复制进 exam_question。
 *       发布后题库再改，历史试卷与成绩都不受影响。</li>
 *   <li><b>发布即冻结</b>：已发布的考试不能再改题目与总分，只允许调整时间窗。</li>
 *   <li><b>总分强校验</b>：题目分值之和必须等于设定的试卷总分，否则拒绝发布。</li>
 *   <li><b>数据隔离</b>：教师只能操作自己创建的考试；绑定班级也必须是本人名下班级。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private static final int CURRENT_ATTEMPT = 1;

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamClassRepository examClassRepository;
    private final ExamRecordRepository examRecordRepository;
    private final QuestionRepository questionRepository;
    private final QuestionKnowledgeRepository questionKnowledgeRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final StudentClassRepository studentClassRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    // ==================== 教师端：考试管理 ====================

    @Transactional(readOnly = true)
    public PageResult<ExamResponse> page(ExamQuery query) {
        Page<Exam> page = examRepository.search(
                query.status(),
                currentUserId(),
                query.normalizedKeyword(),
                PageRequest.of(query.pageIndex(), query.pageSize(), Sort.by(Sort.Direction.DESC, "id")));

        List<ExamResponse> list = page.getContent().stream().map(this::toResponse).toList();
        return PageResult.of(list, page.getTotalElements(), query.pageIndex() + 1, query.pageSize());
    }

    @Transactional(readOnly = true)
    public ExamDetailResponse detail(Long examId) {
        Exam exam = requireOwned(examId);
        return toDetail(exam);
    }

    /** 新建考试草稿 */
    @Transactional
    public ExamResponse create(ExamSaveRequest request) {
        validateTimeWindow(request.startTime(), request.endTime());
        validatePassScore(request.passScore(), request.totalScore());

        Exam exam = new Exam();
        applySettings(exam, request);
        exam.setStatus(ExamStatus.DRAFT);
        exam.setCreatedBy(currentUser());
        examRepository.save(exam);
        log.info("教师 {} 新建考试草稿 {}（{}）", currentUserId(), exam.getId(), exam.getTitle());
        return toResponse(exam);
    }

    /** 修改草稿：只有 DRAFT 状态可改，已发布考试只能调整时间窗 */
    @Transactional
    public ExamResponse update(Long examId, ExamSaveRequest request) {
        Exam exam = requireOwned(examId);
        requireDraft(exam, "只有草稿状态的考试可以修改题目与设置");

        validateTimeWindow(request.startTime(), request.endTime());
        validatePassScore(request.passScore(), request.totalScore());
        applySettings(exam, request);
        examRepository.save(exam);
        return toResponse(exam);
    }

    /**
     * 调整时间窗。
     * <p>
     * 单独开放这一项：考试发布后常需要延长或改期，但题目与总分必须冻结，
     * 否则成绩口径会变。已结束的考试不允许再调整。
     */
    @Transactional
    public ExamResponse updateSchedule(Long examId, ExamScheduleRequest request) {
        Exam exam = requireOwned(examId);
        if (exam.getStatus() == ExamStatus.FINISHED) {
            throw BusinessException.of(ErrorCode.EXAM_STATUS_INVALID, "考试已结束，不能再调整时间");
        }
        validateTimeWindow(request.startTime(), request.endTime());
        exam.setStartTime(request.startTime());
        exam.setEndTime(request.endTime());
        exam.setDuration(request.duration());
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        return toResponse(exam);
    }

    /** 删除考试：只有草稿可删，且要一并清理组卷快照与发布范围 */
    @Transactional
    public void delete(Long examId) {
        Exam exam = requireOwned(examId);
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw BusinessException.of(ErrorCode.EXAM_STATUS_INVALID,
                    "已发布的考试不能删除，请先结束；如需下线请调整时间窗");
        }
        examQuestionRepository.deleteByExamId(examId);
        examClassRepository.deleteByExamId(examId);
        exam.setDeleted(true);
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        log.info("删除考试草稿 {}", examId);
    }

    // ==================== 教师端：组卷 ====================

    /**
     * 手动组卷：整体替换试卷题目。
     * <p>
     * 每道题都会从题库复制一份快照。之所以"整体替换"而不是增量增删：
     * 组卷是整体决策，替换能保证前后端状态一致，也便于草稿阶段反复调整。
     */
    @Transactional
    public ExamDetailResponse setQuestions(Long examId, ExamQuestionsRequest request) {
        Exam exam = requireOwned(examId);
        requireDraft(exam, "只有草稿状态的考试可以修改题目");

        List<ExamQuestionsRequest.Item> items = request.items();
        Set<Long> seen = new HashSet<>();
        for (ExamQuestionsRequest.Item item : items) {
            if (!seen.add(item.questionId())) {
                throw BusinessException.of(ErrorCode.EXAM_QUESTION_DUPLICATED,
                        "题目 " + item.questionId() + " 在试卷中重复出现");
            }
        }

        List<Long> ids = items.stream().map(ExamQuestionsRequest.Item::questionId).toList();
        Map<Long, Question> questionMap = questionRepository.findByIdInAndDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        if (questionMap.size() != ids.size()) {
            throw BusinessException.of(ErrorCode.QUESTION_NOT_FOUND, "存在已被删除的题目，请重新选择");
        }

        examQuestionRepository.deleteByExamIdAndAttemptNo(examId, CURRENT_ATTEMPT);
        List<ExamQuestion> snapshots = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ExamQuestionsRequest.Item item = items.get(i);
            Question q = questionMap.get(item.questionId());
            snapshots.add(ExamQuestion.snapshot(exam, q, item.score(), i + 1));
        }
        examQuestionRepository.saveAll(snapshots);
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        log.info("考试 {} 组卷完成，共 {} 题", examId, snapshots.size());
        return toDetail(exam);
    }

    /**
     * 随机组卷：按「题型 + 难度 + 数量 + 每题分值」抽题，替换当前试卷。
     * <p>
     * 不做知识点覆盖度、难度比例的约束求解——那是 NP 问题，本科阶段做不好也验证不了。
     * 这里只保证"不重复、数量够、可复现地记录抽题结果"，抽完由教师人工检查调整。
     */
    @Transactional
    public ExamDetailResponse randomAssemble(Long examId, RandomAssembleRequest request) {
        Exam exam = requireOwned(examId);
        requireDraft(exam, "只有草稿状态的考试可以随机组卷");

        List<ExamQuestionsRequest.Item> picked = new ArrayList<>();
        Set<Long> used = new LinkedHashSet<>();

        for (RandomAssembleRequest.Rule rule : request.rules()) {
            List<Long> candidates = questionRepository.findIdsForAssembly(
                            request.courseId(), rule.type(), rule.difficulty())
                    .stream()
                    .filter(id -> !used.contains(id))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (candidates.size() < rule.count()) {
                throw BusinessException.of(ErrorCode.EXAM_QUESTION_NOT_ENOUGH,
                        "题型「" + rule.type().getLabel() + "」符合条件的题目只有 " + candidates.size()
                                + " 道，不足 " + rule.count() + " 道");
            }
            Collections.shuffle(candidates);
            for (int i = 0; i < rule.count(); i++) {
                Long id = candidates.get(i);
                used.add(id);
                picked.add(new ExamQuestionsRequest.Item(id, rule.score()));
            }
        }

        if (picked.isEmpty()) {
            throw BusinessException.of(ErrorCode.EXAM_NO_QUESTIONS, "抽题规则没有产生任何题目");
        }
        log.info("考试 {} 随机组卷：{} 条规则共抽 {} 题", examId, request.rules().size(), picked.size());
        return setQuestions(examId, new ExamQuestionsRequest(picked));
    }

    /** 绑定参考班级：替换式绑定，班级必须属于当前教师 */
    @Transactional
    public ExamDetailResponse bindClasses(Long examId, ClassBindRequest request) {
        Exam exam = requireOwned(examId);
        requireDraft(exam, "只有草稿状态的考试可以修改参考班级");

        List<Long> classIds = request.classIds().stream().distinct().toList();
        List<StudentClass> classes = studentClassRepository.findAllById(classIds);
        if (classes.size() != classIds.size()) {
            throw BusinessException.of(ErrorCode.CLASS_NOT_FOUND, "存在不存在的班级");
        }
        for (StudentClass c : classes) {
            if (c.getDeleted()) {
                throw BusinessException.of(ErrorCode.CLASS_NOT_FOUND, "班级「" + c.getName() + "」已被删除");
            }
            if (c.getTeacher() != null && !c.getTeacher().getId().equals(currentUserId())) {
                throw BusinessException.of(ErrorCode.CLASS_NOT_OWNED,
                        "班级「" + c.getName() + "」不属于当前教师");
            }
        }

        examClassRepository.deleteByExamId(examId);
        examClassRepository.saveAll(classIds.stream().map(id -> ExamClass.of(examId, id)).toList());
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        return toDetail(exam);
    }

    // ==================== 教师端：状态流转 ====================

    /**
     * 发布考试。
     * <p>
     * 这是阶段 4 的核心校验点，一次性把关五项：
     * 有题目、分值之和等于总分、绑定了班级、时间窗合法且未过期、及格线不超过总分。
     * 提前拦住比让学生考到一半发现卷子有问题要好得多。
     */
    @Transactional
    public ExamResponse publish(Long examId) {
        Exam exam = requireOwned(examId);
        requireDraft(exam, "只有草稿状态的考试可以发布");

        int count = (int) examQuestionRepository.countByExamIdAndAttemptNo(examId, CURRENT_ATTEMPT);
        if (count == 0) {
            throw BusinessException.of(ErrorCode.EXAM_NO_QUESTIONS);
        }

        int sum = examQuestionRepository.findByExamIdAndAttemptNoOrderBySortOrderAsc(examId, CURRENT_ATTEMPT)
                .stream().mapToInt(ExamQuestion::getScore).sum();
        if (sum != exam.getTotalScore()) {
            throw BusinessException.of(ErrorCode.EXAM_SCORE_MISMATCH,
                    "题目分值之和为 " + sum + "，与试卷总分 " + exam.getTotalScore() + " 不一致");
        }

        if (examClassRepository.findByExamId(examId).isEmpty()) {
            throw BusinessException.of(ErrorCode.EXAM_NO_CLASS);
        }

        validateTimeWindow(exam.getStartTime(), exam.getEndTime());
        if (!exam.getEndTime().isAfter(LocalDateTime.now())) {
            throw BusinessException.of(ErrorCode.EXAM_TIME_INVALID, "考试结束时间已过，请先调整时间窗");
        }

        examineScoreIntegrity(exam);

        exam.setStatus(ExamStatus.PUBLISHED);
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        log.info("考试 {} 已发布（{} 题，总分 {}）", examId, count, sum);
        return toResponse(exam);
    }

    /** 结束考试：发布后才能结束；结束后学生不能再进入 */
    @Transactional
    public ExamResponse finish(Long examId) {
        Exam exam = requireOwned(examId);
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw BusinessException.of(ErrorCode.EXAM_STATUS_INVALID, "只有已发布的考试可以结束");
        }
        exam.setStatus(ExamStatus.FINISHED);
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);
        log.info("考试 {} 已结束", examId);
        return toResponse(exam);
    }

    // ==================== 学生端 ====================

    /**
     * 我的考试：只返回发布给本人所在班级的考试。
     * <p>
     * 未分班学生返回空列表——没有班级就没有考试归属，这是数据隔离的自然结果。
     */
    @Transactional(readOnly = true)
    public List<StudentExamResponse> myExams() {
        User student = currentUser();
        if (student.getStudentClass() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return examRepository.findVisibleToClass(student.getStudentClass().getId()).stream()
                .map(e -> StudentExamResponse.from(
                        e,
                        (int) examQuestionRepository.countByExamIdAndAttemptNo(e.getId(), CURRENT_ATTEMPT),
                        resolvePhase(e, now)))
                .toList();
    }

    /**
     * 计算学生看到的时间阶段。
     * <p>
     * 关键细节：教师手动「结束」的考试，即使时间窗还没到，也必须显示为已结束——
     * 否则会出现「状态是已结束、阶段却写未开始」的自相矛盾展示。
     */
    private ExamPhase resolvePhase(Exam exam, LocalDateTime now) {
        if (exam.getStatus() == ExamStatus.FINISHED) {
            return ExamPhase.ENDED;
        }
        return ExamPhase.of(exam.getStartTime(), exam.getEndTime(), now);
    }

    // ==================== 内部方法 ====================

    private void applySettings(Exam exam, ExamSaveRequest request) {
        exam.setTitle(request.title().trim());
        exam.setDescription(trimToNull(request.description()));
        exam.setCourse(resolveCourse(request.courseId()));
        exam.setTotalScore(request.totalScore());
        exam.setPassScore(request.passScore());
        exam.setStartTime(request.startTime());
        exam.setEndTime(request.endTime());
        exam.setDuration(request.duration());
        exam.setShuffleQuestion(Boolean.TRUE.equals(request.shuffleQuestion()));
        exam.setShuffleOption(Boolean.TRUE.equals(request.shuffleOption()));
        exam.setMaxAttempt(request.maxAttempt() == null ? 1 : request.maxAttempt());
        exam.setUpdatedAt(LocalDateTime.now());
    }

    private Course resolveCourse(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return courseRepository.findByIdAndDeletedFalse(courseId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID, "课程不存在：" + courseId));
    }

    private void validateTimeWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw BusinessException.of(ErrorCode.EXAM_TIME_INVALID, "开始时间与结束时间不能为空");
        }
        if (!end.isAfter(start)) {
            throw BusinessException.of(ErrorCode.EXAM_TIME_INVALID, "结束时间必须晚于开始时间");
        }
    }

    private void validatePassScore(Integer passScore, Integer totalScore) {
        if (passScore == null || passScore < 0) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "及格线不能为负");
        }
        if (totalScore != null && passScore > totalScore) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "及格线不能高于试卷总分");
        }
    }

    private void examineScoreIntegrity(Exam exam) {
        validatePassScore(exam.getPassScore(), exam.getTotalScore());
    }

    private Exam requireOwned(Long examId) {
        Exam exam = examRepository.findByIdAndDeletedFalse(examId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EXAM_NOT_FOUND));
        if (exam.getCreatedBy() != null && !exam.getCreatedBy().getId().equals(currentUserId())) {
            throw BusinessException.of(ErrorCode.EXAM_NOT_OWNED);
        }
        return exam;
    }

    private void requireDraft(Exam exam, String message) {
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw BusinessException.of(ErrorCode.EXAM_STATUS_INVALID, message);
        }
    }

    private ExamResponse toResponse(Exam exam) {
        List<ExamQuestion> questions = examQuestionRepository
                .findByExamIdAndAttemptNoOrderBySortOrderAsc(exam.getId(), CURRENT_ATTEMPT);
        int sum = questions.stream().mapToInt(ExamQuestion::getScore).sum();
        List<String> classNames = loadClassBriefs(exam.getId()).stream()
                .map(ExamDetailResponse.ClassBrief::name).toList();
        return ExamResponse.from(exam, questions.size(), sum, classNames);
    }

    private ExamDetailResponse toDetail(Exam exam) {
        List<ExamQuestion> questions = examQuestionRepository
                .findByExamIdAndAttemptNoOrderBySortOrderAsc(exam.getId(), CURRENT_ATTEMPT);
        int sum = questions.stream().mapToInt(ExamQuestion::getScore).sum();
        List<ExamDetailResponse.ClassBrief> classes = loadClassBriefs(exam.getId());

        Map<Long, List<KnowledgePoint>> kpMap = loadQuestionKnowledge(
                questions.stream().map(ExamQuestion::getQuestionId).toList());

        List<ExamQuestionView> views = questions.stream().map(eq -> {
            List<KnowledgePoint> kps = kpMap.getOrDefault(eq.getQuestionId(), List.of());
            return new ExamQuestionView(
                    eq.getQuestionId(),
                    eq.getType(),
                    eq.getType() == null ? null : eq.getType().getLabel(),
                    eq.getStem(),
                    eq.getOptions() == null ? List.of() : eq.getOptions(),
                    eq.getAnswer() == null ? List.of() : eq.getAnswer(),
                    eq.getScore(),
                    eq.getAnalysis(),
                    eq.getSortOrder(),
                    kps.stream().map(KnowledgePoint::getId).toList(),
                    kps.stream().map(KnowledgePoint::getName).toList());
        }).toList();

        return new ExamDetailResponse(
                exam.getId(),
                exam.getTitle(),
                exam.getDescription(),
                exam.getCourse() == null ? null : exam.getCourse().getId(),
                exam.getCourse() == null ? null : exam.getCourse().getName(),
                exam.getTotalScore(),
                exam.getPassScore(),
                sum,
                views.size(),
                Objects.equals(sum, exam.getTotalScore()),
                exam.getStartTime(),
                exam.getEndTime(),
                exam.getDuration(),
                exam.getShuffleQuestion(),
                exam.getShuffleOption(),
                exam.getMaxAttempt(),
                exam.getStatus(),
                exam.getStatus() == null ? null : exam.getStatus().getLabel(),
                classes,
                views,
                exam.getCreatedAt());
    }

    /** 取考试绑定的班级；班级被删除时跳过，避免详情页因历史数据报错 */
    private List<ExamDetailResponse.ClassBrief> loadClassBriefs(Long examId) {
        List<Long> ids = examClassRepository.findByExamId(examId).stream()
                .map(ExamClass::getClassId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, StudentClass> map = studentClassRepository.findAllById(ids).stream()
                .filter(c -> !c.getDeleted())
                .collect(Collectors.toMap(StudentClass::getId, c -> c));
        List<ExamDetailResponse.ClassBrief> result = new ArrayList<>();
        for (Long id : ids) {
            StudentClass c = map.get(id);
            if (c != null) {
                result.add(new ExamDetailResponse.ClassBrief(c.getId(), c.getName(), c.getGrade()));
            }
        }
        return result;
    }

    /** 批量取题目的知识点，避免详情页 N+1 */
    private Map<Long, List<KnowledgePoint>> loadQuestionKnowledge(List<Long> questionIds) {
        Map<Long, List<KnowledgePoint>> map = new HashMap<>();
        if (questionIds == null || questionIds.isEmpty()) {
            return map;
        }
        for (Object[] row : questionKnowledgeRepository.findKnowledgeByQuestionIds(questionIds)) {
            Long qid = (Long) row[0];
            KnowledgePoint kp = (KnowledgePoint) row[1];
            map.computeIfAbsent(qid, k -> new ArrayList<>()).add(kp);
        }
        return map;
    }

    private User currentUser() {
        return userRepository.findByIdAndDeletedFalse(currentUserId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId();
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
