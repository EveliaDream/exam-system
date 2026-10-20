package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.domain.KnowledgePoint;
import com.finalproject.examsystem.dto.knowledge.KnowledgePointQuery;
import com.finalproject.examsystem.dto.knowledge.KnowledgePointResponse;
import com.finalproject.examsystem.dto.knowledge.KnowledgePointSaveRequest;
import com.finalproject.examsystem.repository.KnowledgePointRepository;
import com.finalproject.examsystem.repository.QuestionKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识点管理。
 * <p>
 * 定位说明：知识点是学情分析的主干——学生掌握程度由「其作答题目所覆盖知识点」的正确率聚合而来。
 * 因此这里不只是维护一份名称列表，还要保证：
 * <ul>
 *   <li>同一课程内名称唯一（否则统计时会分裂成两个「同名但不同 id」的知识点）</li>
 *   <li>被题目引用时不允许删除（否则历史作答记录的知识点关联失效，学情统计出现空洞）</li>
 *   <li>支持课程维度归类，并为公共课保留「通用知识点」</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePointService {

    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionKnowledgeRepository questionKnowledgeRepository;

    @Transactional(readOnly = true)
    public PageResult<KnowledgePointResponse> page(KnowledgePointQuery query) {
        Page<KnowledgePoint> page = knowledgePointRepository.search(
                query.courseId(),
                query.normalizedKeyword(),
                PageRequest.of(query.pageIndex(), query.pageSize(),
                        Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "id"))));

        List<KnowledgePointResponse> list = page.getContent().stream()
                .map(kp -> KnowledgePointResponse.from(
                        kp, questionKnowledgeRepository.countByKnowledgePointId(kp.getId())))
                .toList();
        return PageResult.of(list, page.getTotalElements(), query.pageIndex() + 1, query.pageSize());
    }

    /** 下拉选项：按课程取全部可用知识点（不分页），供录题时选择 */
    @Transactional(readOnly = true)
    public List<KnowledgePointResponse> options(Long courseId) {
        List<KnowledgePoint> points = (courseId == null)
                ? knowledgePointRepository.findByDeletedFalseOrderBySortOrderAscIdAsc()
                : knowledgePointRepository.findByCourseIdAndDeletedFalseOrderBySortOrderAscIdAsc(courseId);
        return points.stream()
                .filter(kp -> Boolean.TRUE.equals(kp.getEnabled()))
                .map(kp -> KnowledgePointResponse.from(
                        kp, questionKnowledgeRepository.countByKnowledgePointId(kp.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgePointResponse detail(Long id) {
        KnowledgePoint kp = require(id);
        return KnowledgePointResponse.from(kp, questionKnowledgeRepository.countByKnowledgePointId(id));
    }

    @Transactional
    public KnowledgePointResponse create(KnowledgePointSaveRequest request) {
        String name = request.name().trim();
        if (knowledgePointRepository.existsSameName(name, request.courseId(), null)) {
            throw BusinessException.of(ErrorCode.KNOWLEDGE_POINT_DUPLICATED);
        }
        KnowledgePoint kp = KnowledgePoint.create(
                name,
                trimToNull(request.description()),
                request.courseId(),
                request.sortOrder() == null ? 0 : request.sortOrder());
        knowledgePointRepository.save(kp);
        log.info("新增知识点 {}", name);
        return KnowledgePointResponse.from(kp, 0);
    }

    @Transactional
    public KnowledgePointResponse update(Long id, KnowledgePointSaveRequest request) {
        KnowledgePoint kp = require(id);
        String name = request.name().trim();
        // 排除自身，避免"不改名直接保存"误报重名
        if (knowledgePointRepository.existsSameName(name, request.courseId(), id)) {
            throw BusinessException.of(ErrorCode.KNOWLEDGE_POINT_DUPLICATED);
        }
        kp.setName(name);
        kp.setDescription(trimToNull(request.description()));
        kp.setCourseId(request.courseId());
        if (request.sortOrder() != null) {
            kp.setSortOrder(request.sortOrder());
        }
        kp.setUpdatedAt(LocalDateTime.now());
        knowledgePointRepository.save(kp);
        return KnowledgePointResponse.from(kp, questionKnowledgeRepository.countByKnowledgePointId(id));
    }

    /**
     * 删除知识点（逻辑删除）。
     * <p>
     * 已被题目引用时拒绝删除：关联表记录会失去指向，历史作答的学情统计将无法按该知识点聚合，
     * 出现「统计总数对不上」的问题。若确实要停用，可改为设置 enabled=false。
     */
    @Transactional
    public void delete(Long id) {
        KnowledgePoint kp = require(id);
        long used = questionKnowledgeRepository.countByKnowledgePointId(id);
        if (used > 0) {
            throw BusinessException.of(ErrorCode.KNOWLEDGE_POINT_IN_USE,
                    "该知识点已被 " + used + " 道题引用，请先解除关联；如需停用请改为禁用");
        }
        kp.setDeleted(true);
        kp.setUpdatedAt(LocalDateTime.now());
        knowledgePointRepository.save(kp);
        log.info("删除知识点 {}", id);
    }

    /** 供题目模块校验：知识点必须存在且未删除 */
    @Transactional(readOnly = true)
    public List<KnowledgePoint> requireAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<KnowledgePoint> found = knowledgePointRepository.findAllById(ids);
        List<Long> foundIds = found.stream().map(KnowledgePoint::getId).toList();
        List<Long> missing = ids.stream().filter(i -> !foundIds.contains(i)).toList();
        if (!missing.isEmpty()) {
            throw BusinessException.of(ErrorCode.KNOWLEDGE_POINT_NOT_FOUND,
                    "知识点不存在或已删除：" + missing);
        }
        return found;
    }

    private KnowledgePoint require(Long id) {
        return knowledgePointRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.KNOWLEDGE_POINT_NOT_FOUND));
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
