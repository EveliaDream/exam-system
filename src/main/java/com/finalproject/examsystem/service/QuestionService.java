package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.domain.Course;
import com.finalproject.examsystem.domain.KnowledgePoint;
import com.finalproject.examsystem.domain.Question;
import com.finalproject.examsystem.domain.QuestionKnowledge;
import com.finalproject.examsystem.domain.QuestionType;
import com.finalproject.examsystem.dto.question.QuestionQuery;
import com.finalproject.examsystem.dto.question.QuestionResponse;
import com.finalproject.examsystem.dto.question.QuestionSaveRequest;
import com.finalproject.examsystem.repository.CourseRepository;
import com.finalproject.examsystem.repository.KnowledgePointRepository;
import com.finalproject.examsystem.repository.QuestionKnowledgeRepository;
import com.finalproject.examsystem.repository.QuestionRepository;
import com.finalproject.examsystem.security.SecurityUtils;
import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 题库管理。
 * <p>
 * 本模块是学情分析的数据源头：题目上的分值与知识点决定了「按知识点聚合正确率」能否成立，
 * 因此录入时对题型与答案结构做严格校验，避免脏数据污染后续统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final List<String> OPTION_LABELS =
            List.of("A", "B", "C", "D", "E", "F", "G", "H");

    private final QuestionRepository questionRepository;
    private final QuestionKnowledgeRepository questionKnowledgeRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResult<QuestionResponse> page(QuestionQuery query) {
        Page<Question> page = questionRepository.search(
                query.courseId(),
                query.type(),
                query.difficulty(),
                query.enabled(),
                query.normalizedKeyword(),
                query.knowledgePointId(),
                PageRequest.of(query.pageIndex(), query.pageSize(),
                        Sort.by(Sort.Direction.DESC, "id")));

        Map<Long, List<QuestionResponse.KnowledgeTag>> tagMap = loadTags(
                page.getContent().stream().map(Question::getId).toList());

        List<QuestionResponse> list = page.getContent().stream()
                .map(q -> QuestionResponse.from(q, tagMap.getOrDefault(q.getId(), List.of())))
                .toList();
        return PageResult.of(list, page.getTotalElements(), query.pageIndex() + 1, query.pageSize());
    }

    @Transactional(readOnly = true)
    public QuestionResponse detail(Long id) {
        Question q = require(id);
        Map<Long, List<QuestionResponse.KnowledgeTag>> tagMap = loadTags(List.of(id));
        return QuestionResponse.from(q, tagMap.getOrDefault(id, List.of()));
    }

    @Transactional
    public QuestionResponse create(QuestionSaveRequest request) {
        validate(request, null);

        Question q = new Question();
        apply(q, request);
        q.setCreatedBy(currentUser());
        questionRepository.save(q);

        saveKnowledgeLinks(q.getId(), request.knowledgePointIds());
        log.info("新增题目 {}（{}）", q.getId(), q.getType());
        return detail(q.getId());
    }

    @Transactional
    public QuestionResponse update(Long id, QuestionSaveRequest request) {
        Question q = require(id);
        validate(request, q.getType());

        apply(q, request);
        q.setUpdatedAt(LocalDateTime.now());
        questionRepository.save(q);

        // 关联关系整体替换：先删后加，避免残留旧知识点
        questionKnowledgeRepository.deleteByQuestionId(id);
        saveKnowledgeLinks(id, request.knowledgePointIds());
        return detail(id);
    }

    /**
     * 删除题目（逻辑删除）。
     * <p>
     * 同时解除知识点关联：若不解除，知识点下的「被引用题数」会包含已删除的题，
     * 导致该知识点既删不掉、统计口径也不准。
     */
    @Transactional
    public void delete(Long id) {
        Question q = require(id);
        q.setDeleted(true);
        q.setUpdatedAt(LocalDateTime.now());
        questionRepository.save(q);
        questionKnowledgeRepository.deleteByQuestionId(id);
        log.info("删除题目 {}", id);
    }

    /** 启用/停用题目：停用后不参与抽题 */
    @Transactional
    public QuestionResponse toggleEnabled(Long id, boolean enabled) {
        Question q = require(id);
        q.setEnabled(enabled);
        q.setUpdatedAt(LocalDateTime.now());
        questionRepository.save(q);
        return detail(id);
    }

    // ================= 校验与内部方法 =================

    /**
     * 校验题型与答案结构。
     * <p>
     * 这里是「防脏数据」的关键：如果允许多选题只存一个答案、或判断题答案不是 T/F，
     * 后续自动判分会直接出错，而这些错误一旦进入历史数据就无法修复。
     *
     * @param originalType 修改场景下的原题型，用于判断选项标签语义是否变化；新增时传 null
     */
    private void validate(QuestionSaveRequest request, QuestionType originalType) {
        QuestionType type = request.type();
        List<String> options = request.options() == null ? List.of() : request.options();
        List<String> answer = request.answer() == null ? List.of() : request.answer();

        if (type == QuestionType.BLANK) {
            if (answer.isEmpty() || answer.stream().allMatch(a -> a == null || a.isBlank())) {
                throw BusinessException.of(ErrorCode.QUESTION_INVALID, "填空题至少需要一个可接受答案");
            }
        } else {
            if (options.size() < 2) {
                throw BusinessException.of(ErrorCode.QUESTION_INVALID, "选择题/判断题至少需要 2 个选项");
            }
            if (options.stream().anyMatch(o -> o == null || o.isBlank())) {
                throw BusinessException.of(ErrorCode.QUESTION_INVALID, "选项内容不能为空");
            }
            if (new HashSet<>(options).size() != options.size()) {
                throw BusinessException.of(ErrorCode.QUESTION_INVALID, "选项内容不能重复");
            }
        }

        switch (type) {
            case SINGLE -> {
                if (answer.size() != 1) {
                    throw BusinessException.of(ErrorCode.QUESTION_INVALID, "单选题必须且只能有一个正确答案");
                }
                requireValidOptionLabel(answer.get(0), options.size());
            }
            case MULTIPLE -> {
                if (answer.size() < 2) {
                    throw BusinessException.of(ErrorCode.QUESTION_INVALID, "多选题至少需要两个正确答案");
                }
                if (new HashSet<>(answer).size() != answer.size()) {
                    throw BusinessException.of(ErrorCode.QUESTION_INVALID, "多选题答案不能重复");
                }
                for (String a : answer) {
                    requireValidOptionLabel(a, options.size());
                }
            }
            case JUDGE -> {
                if (answer.size() != 1 || !List.of("T", "F").contains(answer.get(0))) {
                    throw BusinessException.of(ErrorCode.QUESTION_INVALID, "判断题答案必须为 T 或 F");
                }
            }
            case BLANK -> {
                // 已在上方校验
            }
            default -> throw BusinessException.of(ErrorCode.QUESTION_INVALID, "不支持的题型");
        }
    }

    /** 答案标识必须是 A、B、C… 且不超过选项数量 */
    private void requireValidOptionLabel(String label, int optionCount) {
        if (label == null || label.length() != 1) {
            throw BusinessException.of(ErrorCode.QUESTION_INVALID, "答案标识不合法：" + label);
        }
        int index = OPTION_LABELS.indexOf(label.toUpperCase());
        if (index < 0 || index >= optionCount) {
            throw BusinessException.of(ErrorCode.QUESTION_INVALID,
                    "答案 " + label + " 超出选项范围（共 " + optionCount + " 项）");
        }
    }

    private void apply(Question q, QuestionSaveRequest request) {
        q.setType(request.type());
        q.setStem(request.stem().trim());
        q.setOptions(request.type() == QuestionType.BLANK
                ? List.of()
                : request.options().stream().map(String::trim).toList());
        q.setAnswer(request.answer().stream().map(String::trim).toList());
        q.setScore(request.score());
        q.setDifficulty(request.difficulty());
        q.setAnalysis(request.analysis() == null || request.analysis().isBlank()
                ? null : request.analysis().trim());
        q.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        q.setCourse(resolveCourse(request.courseId()));
    }

    private Course resolveCourse(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return courseRepository.findByIdAndDeletedFalse(courseId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID, "课程不存在：" + courseId));
    }

    private void saveKnowledgeLinks(Long questionId, List<Long> knowledgePointIds) {
        if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
            return;
        }
        List<Long> distinct = knowledgePointIds.stream().distinct().toList();
        List<KnowledgePoint> found = knowledgePointRepository.findAllById(distinct);
        if (found.size() != distinct.size()) {
            throw BusinessException.of(ErrorCode.KNOWLEDGE_POINT_NOT_FOUND, "存在无效的知识点");
        }
        List<QuestionKnowledge> links = new ArrayList<>();
        for (Long kpId : distinct) {
            links.add(QuestionKnowledge.of(questionId, kpId));
        }
        questionKnowledgeRepository.saveAll(links);
    }

    /** 批量取知识点标签，避免列表页 N+1 */
    private Map<Long, List<QuestionResponse.KnowledgeTag>> loadTags(List<Long> questionIds) {
        Map<Long, List<QuestionResponse.KnowledgeTag>> map = new HashMap<>();
        if (questionIds == null || questionIds.isEmpty()) {
            return map;
        }
        for (Object[] row : questionKnowledgeRepository.findKnowledgeByQuestionIds(questionIds)) {
            Long qid = (Long) row[0];
            KnowledgePoint kp = (KnowledgePoint) row[1];
            map.computeIfAbsent(qid, k -> new ArrayList<>())
                    .add(new QuestionResponse.KnowledgeTag(kp.getId(), kp.getName()));
        }
        return map;
    }

    private Question require(Long id) {
        return questionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.QUESTION_NOT_FOUND));
    }

    private User currentUser() {
        return userRepository.findByIdAndDeletedFalse(SecurityUtils.currentUserId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
    }
}
