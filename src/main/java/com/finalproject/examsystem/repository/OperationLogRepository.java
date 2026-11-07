package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    /**
     * 日志查询：三个条件都可为空。
     * <p>
     * 用 `(:param is null or ...)` 而不是写四条不同方法：
     * 日志筛选组合多（动作/操作人/时间），拼接查询会迅速失控。
     */
    @Query("""
            select l from OperationLog l
            where (:action is null or l.action = :action)
              and (:keyword is null or l.username like %:keyword% or l.target like %:keyword%)
              and (:from is null or l.createdAt >= :from)
              and (:to is null or l.createdAt <= :to)
            """)
    Page<OperationLog> search(@Param("action") String action,
                              @Param("keyword") String keyword,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              Pageable pageable);

    /** 取最近发生过的动作类型，供前端渲染下拉筛选（避免把常量写死在前端） */
    @Query("select distinct l.action from OperationLog l order by l.action")
    java.util.List<String> findDistinctActions();
}
