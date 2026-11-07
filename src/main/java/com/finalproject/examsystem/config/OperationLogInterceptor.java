package com.finalproject.examsystem.config;

import com.finalproject.examsystem.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 操作日志拦截器。
 * <p>
 * 为什么用拦截器而不是在每个 Service 里手写日志：
 * 写操作散落在十几个接口里，逐个加容易漏，而且会让业务代码被审计逻辑污染。
 * 拦截器在框架层统一拦"成功的写请求"，一处覆盖全部。
 * <p>
 * 只记录**成功**的写操作（2xx）：失败的请求大多是参数校验没过，
 * 记下来只会把日志刷满，真正需要追溯的"谁改了成绩"一定是成功的那一次。
 */
@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

    private final OperationLogService operationLogService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        // 只审计写操作
        if (!"POST".equals(method) && !"PUT".equals(method) && !"DELETE".equals(method)
                && !"PATCH".equals(method)) {
            return;
        }
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return;
        }

        String path = request.getRequestURI();
        String action = method + " " + patternize(path);
        String target = path.length() > 190 ? path.substring(0, 190) : path;
        operationLogService.record(action, target, clientIp(request));
    }

    /**
     * 把路径里的数字 id 换成 {id}，让同类操作能聚合。
     * /api/exams/12/publish → /api/exams/{id}/publish
     */
    private String patternize(String path) {
        return path.replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    /** 取真实客户端 IP：经过反向代理时 X-Forwarded-For 的第一个才是原始地址 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.length() > 45 ? first.substring(0, 45) : first;
        }
        String remote = request.getRemoteAddr();
        return remote == null ? null : (remote.length() > 45 ? remote.substring(0, 45) : remote);
    }
}
