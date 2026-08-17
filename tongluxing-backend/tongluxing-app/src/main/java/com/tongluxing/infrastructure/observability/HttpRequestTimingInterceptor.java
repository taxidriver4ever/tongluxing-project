package com.tongluxing.infrastructure.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 全局 HTTP 请求耗时拦截器。
 *
 * <p>拦截器在请求进入 Spring MVC 时记录单调时钟值，并在请求处理完成后计算端到端耗时。
 * 这里使用 {@link System#nanoTime()} 而不是系统时间戳，避免服务器校时或时钟回拨影响耗时结果。</p>
 *
 * <p>日志只包含请求方法、请求路径、响应状态码和总耗时，不读取查询参数、请求体、请求头或用户信息，
 * 因而不会改变业务处理流程，也能避免敏感数据被写入日志。</p>
 */
@Slf4j
@Component
public class HttpRequestTimingInterceptor implements HandlerInterceptor {

    /**
     * 请求属性名使用类全限定名作为前缀，避免与业务代码写入的 request attribute 冲突。
     */
    private static final String START_NANOS_ATTRIBUTE =
            HttpRequestTimingInterceptor.class.getName() + ".startNanos";

    /** 超过该耗时的请求使用 WARN 级别记录，单位为毫秒。 */
    private final long slowRequestThresholdMs;

    public HttpRequestTimingInterceptor(
            @Value("${observability.http.slow-request-threshold-ms:2000}") long slowRequestThresholdMs) {
        // 对错误的负数配置做安全兜底：阈值归零后所有有耗时的请求都会被突出显示，而不会影响应用启动。
        this.slowRequestThresholdMs = Math.max(0L, slowRequestThresholdMs);
    }

    /**
     * 在 Controller 执行前记录请求起始时间。
     *
     * <p>异步请求完成后可能再次进入 MVC 分派，因此仅在属性不存在时写入起始时间，确保最终统计的是
     * 从首次进入应用到响应完成的总耗时，而不是最后一次分派的局部耗时。</p>
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (request.getAttribute(START_NANOS_ATTRIBUTE) == null) {
            request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        }

        // 返回 true 表示继续原有处理链；拦截器只负责计时，不阻断或修改业务请求。
        return true;
    }

    /**
     * 请求处理完成后输出统一的接口耗时日志。
     *
     * <p>{@code afterCompletion} 会在 Controller、视图渲染及异常处理结束后执行，此时可以读取最终 HTTP
     * 状态码。普通请求使用 INFO，严格超过慢请求阈值时使用 WARN，便于在日志平台中快速筛选卡顿接口。</p>
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        Object startNanosValue = request.getAttribute(START_NANOS_ATTRIBUTE);
        if (!(startNanosValue instanceof Long startNanos)) {
            // 理论上不会发生；保留防御性判断，避免异常分派缺少起始值时影响正常响应。
            return;
        }

        long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();

        // 计算完成后立即移除属性，防止同一请求对象发生后续分派时重复使用已经结束的计时数据。
        request.removeAttribute(START_NANOS_ATTRIBUTE);

        if (durationMs > slowRequestThresholdMs) {
            log.warn(
                    "Slow HTTP request method={} path={} status={} durationMs={} thresholdMs={}",
                    method,
                    path,
                    status,
                    durationMs,
                    slowRequestThresholdMs);
            return;
        }

        log.info(
                "HTTP request method={} path={} status={} durationMs={}",
                method,
                path,
                status,
                durationMs);
    }
}
