package com.tongluxing.infrastructure.observability;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 可观测性配置。
 *
 * <p>该配置只把接口耗时拦截器注册到全局 MVC 请求链，不改变参数解析、异常处理、鉴权或业务调用规则。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcObservabilityConfiguration implements WebMvcConfigurer {

    private final HttpRequestTimingInterceptor httpRequestTimingInterceptor;

    /**
     * 对所有进入 Spring MVC 的 HTTP 路径启用耗时统计。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpRequestTimingInterceptor)
                .addPathPatterns("/**");
    }
}
