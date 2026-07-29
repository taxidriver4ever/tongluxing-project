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
 * Spring Security 认证与授权失败的统一 JSON 输出器。
 *
 * <p>过滤器链中的异常不会进入 MVC 的 {@code @RestControllerAdvice}，因此同时实现
 * {@link AuthenticationEntryPoint} 和 {@link AccessDeniedHandler}，保证 401、403 与业务接口
 * 使用相同的 {@code Result} JSON 结构。另提供“账号被其他设备登录”专用业务码，
 * 便于客户端区别于自然过期并展示明确提示。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** 未建立认证主体时返回的用户提示。 */
    private static final String LOGIN_REQUIRED_MESSAGE = "请先登录";
    /** 已认证但角色不满足路径规则时返回的用户提示。 */
    private static final String FORBIDDEN_MESSAGE = "权限不足";
    /** 旧会话被新登录替换时使用的稳定业务错误码。 */
    public static final int ACCOUNT_LOGGED_IN_ELSEWHERE_CODE = 40101;
    /** 旧会话被顶下线的统一提示文本。 */
    public static final String ACCOUNT_LOGGED_IN_ELSEWHERE_MESSAGE = "账号已在其他设备登录，请重新登录";

    /** 将 Result 对象序列化到 ServletResponse。 */
    private final ObjectMapper objectMapper;

    /** 未认证请求访问受保护资源时输出 401 Result。 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException authException) throws IOException {
        // 没有建立 Authentication 或 Token 普通失效时，统一输出 401“请先登录”。
        write(response, ResultCode.UNAUTHORIZED, LOGIN_REQUIRED_MESSAGE);
    }

    /** 已认证主体缺少所需角色时输出 403 Result。 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // 已认证但角色不满足 hasRole/hasAuthority 规则时输出 403，不混同为登录失效。
        write(response, ResultCode.FORBIDDEN, FORBIDDEN_MESSAGE);
    }

    /** 旧会话被新登录剔除时返回可被前端精确识别的业务码。 */
    public void accountLoggedInElsewhere(HttpServletResponse response) throws IOException {
        // HTTP 状态仍为 401，但业务码使用 40101，供客户端展示被顶下线专用提示。
        write(response, ResultCode.UNAUTHORIZED.getCode(), ACCOUNT_LOGGED_IN_ELSEWHERE_CODE,
                ACCOUNT_LOGGED_IN_ELSEWHERE_MESSAGE);
    }

    /** 使用同一个 ResultCode 作为 HTTP 状态与默认业务码。 */
    private void write(HttpServletResponse response, ResultCode resultCode, String message) throws IOException {
        // 常规错误使用 ResultCode 同时提供 HTTP 状态码与业务码。
        write(response, resultCode.getCode(), resultCode.getCode(), message);
    }

    /**
     * 写入最终 JSON 响应。
     *
     * <p>若响应已经提交则不再二次写入，避免覆盖下游已经产生的内容或触发
     * {@code IllegalStateException}。</p>
     */
    private void write(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        // 响应已由上游或下游提交时不能再次设置状态和写 JSON。
        if (response.isCommitted()) return;
        // 先设置 HTTP 状态，再明确 UTF-8 与 JSON Content-Type，保证中文提示正确显示。
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 使用应用统一 Result 结构，前端无需为 Security 失败维护另一套解析逻辑。
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}
