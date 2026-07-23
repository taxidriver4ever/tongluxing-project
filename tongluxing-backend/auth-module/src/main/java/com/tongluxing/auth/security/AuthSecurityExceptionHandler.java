package com.tongluxing.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.result.Result;
import com.tongluxing.common.result.ResultCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Writes Spring Security authentication and authorization failures as Result JSON.
 */
@Component
@RequiredArgsConstructor
public class AuthSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String LOGIN_REQUIRED_MESSAGE = "请先登录";
    private static final String FORBIDDEN_MESSAGE = "权限不足";
    public static final int ACCOUNT_LOGGED_IN_ELSEWHERE_CODE = 40101;
    public static final String ACCOUNT_LOGGED_IN_ELSEWHERE_MESSAGE = "账号已在其他设备登录，请重新登录";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException authException) throws IOException {
        write(response, ResultCode.UNAUTHORIZED, LOGIN_REQUIRED_MESSAGE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ResultCode.FORBIDDEN, FORBIDDEN_MESSAGE);
    }

    /** 旧会话被新登录剔除时返回可被前端精确识别的业务码。 */
    public void accountLoggedInElsewhere(HttpServletResponse response) throws IOException {
        write(response, ResultCode.UNAUTHORIZED.getCode(), ACCOUNT_LOGGED_IN_ELSEWHERE_CODE,
                ACCOUNT_LOGGED_IN_ELSEWHERE_MESSAGE);
    }

    private void write(HttpServletResponse response, ResultCode resultCode, String message) throws IOException {
        write(response, resultCode.getCode(), resultCode.getCode(), message);
    }

    private void write(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}
