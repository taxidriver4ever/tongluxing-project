package com.tongluxing.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RecommendResponseTimingAdviceTest {

    private final RecommendResponseTimingAdvice advice = new RecommendResponseTimingAdvice();

    @Test
    void marksRecommendResponseBeforeSerialization() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trips/recommend");
        Object body = new Object();

        Object returned = advice.beforeBodyWrite(
                body,
                mock(org.springframework.core.MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                null);

        assertThat(returned).isSameAs(body);
        assertThat(request.getAttribute(RecommendRequestLifecycleFilter.BUSINESS_FINISH_NANOS_ATTRIBUTE))
                .isInstanceOf(Long.class);
        assertThat(request.getAttribute(RecommendRequestLifecycleFilter.BUSINESS_FINISH_TIME_ATTRIBUTE))
                .isInstanceOf(String.class);
    }

    @Test
    void ignoresOtherEndpoints() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");

        advice.beforeBodyWrite(
                new Object(),
                mock(org.springframework.core.MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                null);

        assertThat(request.getAttribute(RecommendRequestLifecycleFilter.BUSINESS_FINISH_NANOS_ATTRIBUTE)).isNull();
    }
}
