package com.tongluxing.infrastructure.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 推荐接口端到端生命周期诊断 Filter。
 *
 * <p>只包装 {@code GET /api/v1/trips/recommend}，不会改变推荐算法、缓存、SQL 或响应结构。
 * 响应先写入 {@link ContentCachingResponseWrapper}，用于统计序列化后的真实 UTF-8 字节数，随后原样复制给客户端。</p>
 *
 * <p>{@code backendTotalMs} 的结束点是 Servlet 将响应复制到容器输出缓冲区；它不代表客户端已经收完响应，
 * 因此需要与 Nginx {@code request_time} 和 k6 {@code receiving} 联合判断。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RecommendRequestLifecycleFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String BUSINESS_FINISH_NANOS_ATTRIBUTE =
            RecommendRequestLifecycleFilter.class.getName() + ".businessFinishNanos";
    public static final String BUSINESS_FINISH_TIME_ATTRIBUTE =
            RecommendRequestLifecycleFilter.class.getName() + ".businessFinishTime";

    private static final String RECOMMEND_URI = "/api/v1/trips/recommend";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod())
                || !RECOMMEND_URI.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long receiveNanos = System.nanoTime();
        Instant receiveTime = Instant.now();
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        boolean chainCompleted = false;

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        request.setAttribute(REQUEST_ID_MDC_KEY, requestId);
        cachingResponse.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, cachingResponse);
            chainCompleted = true;
        } finally {
            long commitNanos = System.nanoTime();
            Instant commitTime = Instant.now();
            Object businessFinishValue = request.getAttribute(BUSINESS_FINISH_NANOS_ATTRIBUTE);
            long businessFinishNanos = businessFinishValue instanceof Long value ? value : commitNanos;
            long jsonSerializeMs = elapsedMs(businessFinishNanos, commitNanos);
            int responseBodyBytes = cachingResponse.getContentSize();

            if (chainCompleted) {
                cachingResponse.copyBodyToResponse();
            }

            long finishNanos = System.nanoTime();
            Instant finishTime = Instant.now();
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "recommend_request_lifecycle");
            event.put("requestId", requestId);
            event.put("uri", request.getRequestURI());
            event.put("method", request.getMethod());
            event.put("status", cachingResponse.getStatus());
            event.put("requestReceiveTime", receiveTime.toString());
            event.put("businessFinishTime", request.getAttribute(BUSINESS_FINISH_TIME_ATTRIBUTE));
            event.put("responseCommitTime", commitTime.toString());
            event.put("requestFinishTime", finishTime.toString());
            event.put("backendTotalMs", elapsedMs(receiveNanos, finishNanos));
            event.put("responseCommitMs", jsonSerializeMs);
            event.put("jsonSerializeMs", jsonSerializeMs);
            event.put("responseBodyBytes", responseBodyBytes);
            event.put("responseBytes", responseBodyBytes);
            event.put("chainCompleted", chainCompleted);
            logLifecycle(event);

            if (previousRequestId == null) {
                MDC.remove(REQUEST_ID_MDC_KEY);
            } else {
                MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
            }
        }
    }

    private String requestId(String supplied) {
        if (supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private void logLifecycle(Map<String, Object> event) {
        try {
            log.info("recommend_request_lifecycle {}", objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            log.warn("recommend_request_lifecycle_encode_failed requestId={} reason={}",
                    event.get("requestId"), exception.getClass().getSimpleName());
        }
    }

    private long elapsedMs(long started, long finished) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, finished - started));
    }
}
