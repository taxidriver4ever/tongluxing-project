package com.tongluxing.infrastructure.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Instant;

/** 在推荐响应进入 HttpMessageConverter 前标记时间，用于拆分 JSON 序列化耗时。 */
@ControllerAdvice
public class RecommendResponseTimingAdvice implements ResponseBodyAdvice<Object> {

    private static final String RECOMMEND_URI = "/api/v1/trips/recommend";

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest serverRequest,
            ServerHttpResponse serverResponse) {
        if (serverRequest instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest request = servletRequest.getServletRequest();
            if ("GET".equalsIgnoreCase(request.getMethod())
                    && RECOMMEND_URI.equals(request.getRequestURI())
                    && request.getAttribute(RecommendRequestLifecycleFilter.BUSINESS_FINISH_NANOS_ATTRIBUTE) == null) {
                request.setAttribute(
                        RecommendRequestLifecycleFilter.BUSINESS_FINISH_NANOS_ATTRIBUTE,
                        System.nanoTime());
                request.setAttribute(
                        RecommendRequestLifecycleFilter.BUSINESS_FINISH_TIME_ATTRIBUTE,
                        Instant.now().toString());
            }
        }
        return body;
    }
}
