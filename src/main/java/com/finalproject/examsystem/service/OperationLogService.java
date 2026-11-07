package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.domain.OperationLog;
import com.finalproject.examsystem.dto.log.OperationLogQuery;
import com.finalproject.examsystem.dto.log.OperationLogResponse;
import com.finalproject.examsystem.repository.OperationLogRepository;
import com.finalproject.examsystem.security.SecurityUtils;
import com.finalproject.examsystem.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 操作日志（审计）。
 * <p>
 * 设计原则：**记日志失败绝不能影响业务**。
 * 所以写日志走 {@code REQUIRES_NEW} 独立事务，并在拦截器里捕获所有异常——
 * 日志只是旁路信息，不能因为数据库抖动就让"发布考试"这种主流程失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationLogRepository operationLogRepository;

    /**
     * 记录一条操作日志。
     * <p>
     * REQUIRES_NEW：即使调用方事务回滚了，失败的尝试本身也值得留痕（审计要能看到"试过什么"）；
     * 反过来，日志写入失败也不能把主事务拖下去，所以异常在这里吞掉并只打 warn。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String target, String ip) {
        try {
            UserPrincipal principal = SecurityUtils.currentUserOrNull();
            OperationLog entity = OperationLog.of(
                    principal == null ? null : principal.userId(),
                    principal == null ? null : principal.getUsername(),
                    action,
                    target,
                    ip);
            operationLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("写操作日志失败（不影响主流程）: action={}, target={}", action, target, e);
        }
    }

    /** 分页查询（教师） */
    @Transactional(readOnly = true)
    public PageResult<OperationLogResponse> page(OperationLogQuery query) {
        Page<OperationLog> page = operationLogRepository.search(
                query.normalizedAction(),
                query.normalizedKeyword(),
                parse(query.from()),
                parse(query.to()),
                PageRequest.of(query.pageIndex(), query.pageSize(),
                        Sort.by(Sort.Direction.DESC, "id")));

        List<OperationLogResponse> list = page.getContent().stream()
                .map(OperationLogResponse::from)
                .toList();
        return PageResult.of(list, page.getTotalElements(), query.pageIndex() + 1, query.pageSize());
    }

    /** 已出现过的动作类型，供前端下拉 */
    @Transactional(readOnly = true)
    public List<String> actions() {
        return operationLogRepository.findDistinctActions();
    }

    private LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
