package com.tongluxing.infrastructure.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendRequestLifecycleFilterTest {

    private final RecommendRequestLifecycleFilter filter =
            new RecommendRequestLifecycleFilter(new ObjectMapper());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsSuppliedRequestIdAndCopiesResponseBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trips/recommend");
        request.addHeader(RecommendRequestLifecycleFilter.REQUEST_ID_HEADER, "k6-diag-5-101");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = "{\"code\":200,\"data\":[]}".getBytes(StandardCharsets.UTF_8);
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(
                    RecommendRequestLifecycleFilter.BUSINESS_FINISH_NANOS_ATTRIBUTE,
                    System.nanoTime());
            servletResponse.setContentType("application/json");
            servletResponse.getOutputStream().write(body);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RecommendRequestLifecycleFilter.REQUEST_ID_HEADER))
                .isEqualTo("k6-diag-5-101");
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        assertThat(MDC.get(RecommendRequestLifecycleFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trips/recommend");
        request.addHeader(RecommendRequestLifecycleFilter.REQUEST_ID_HEADER, "unsafe request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(response.getHeader(RecommendRequestLifecycleFilter.REQUEST_ID_HEADER))
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void doesNotWrapOtherEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (ignoredRequest, servletResponse) -> servletResponse.getWriter().write("ok"));

        assertThat(response.getHeader(RecommendRequestLifecycleFilter.REQUEST_ID_HEADER)).isNull();
        assertThat(response.getContentAsString()).isEqualTo("ok");
    }
}
