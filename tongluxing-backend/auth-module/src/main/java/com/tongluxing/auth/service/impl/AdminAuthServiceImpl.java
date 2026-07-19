package com.tongluxing.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.tongluxing.auth.config.AdminAuthProperties;
import com.tongluxing.auth.dto.AdminLoginRequest;
import com.tongluxing.auth.security.AdminLoginRateLimiter;
import com.tongluxing.auth.security.AdminSessionStore;
import com.tongluxing.auth.service.AdminAuthService;
import com.tongluxing.auth.vo.AdminCurrentOperatorResponse;
import com.tongluxing.auth.vo.AdminLoginResponse;
import com.tongluxing.auth.vo.AdminLogoutResponse;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;

import lombok.RequiredArgsConstructor;

/** 固定联调凭据的 Web Admin 登录实现。 */
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {
    private final AdminAuthProperties properties;
    private final AdminLoginRateLimiter rateLimiter;
    private final AdminSessionStore sessionStore;

    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        AdminLoginRateLimiter.LimitState before = rateLimiter.state(username);
        if (before.locked()) {
            throw locked(before);
        }

        boolean usernameMatches = constantTimeEquals(username,
                properties.getUsername().trim().toLowerCase(Locale.ROOT));
        boolean passwordMatches = constantTimeEquals(request.password(), properties.getPassword());
        if (!usernameMatches || !passwordMatches) {
            AdminLoginRateLimiter.LimitState after = rateLimiter.recordFailure(username);
            if (after.locked()) {
                throw locked(after);
            }
            long remaining = properties.getMaxFailures() - after.failures();
            throw new BusinessException(ResultCode.UNAUTHORIZED,
                    "用户名或密码错误，还可尝试 " + remaining + " 次");
        }

        rateLimiter.reset(username);
        AdminSessionStore.CreatedSession created = sessionStore.create(properties.getOperatorId(),
                properties.getUsername(), properties.getDisplayName());
        return new AdminLoginResponse(created.token(), created.session().operatorId(),
                created.session().username(), created.session().displayName(), created.expireSeconds());
    }

    @Override
    public AdminCurrentOperatorResponse current(String authorization) {
        AdminSessionStore.AdminSession session = requireSession(authorization);
        return new AdminCurrentOperatorResponse(session.operatorId(), session.username(),
                session.displayName(), session.expireAt());
    }

    @Override
    public AdminLogoutResponse logout(String authorization) {
        String token = resolveBearer(authorization);
        requireSession(authorization);
        sessionStore.delete(token);
        return new AdminLogoutResponse(true);
    }

    private AdminSessionStore.AdminSession requireSession(String authorization) {
        return sessionStore.resolve(resolveBearer(authorization))
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Admin 登录状态已失效"));
    }

    private String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录 Admin 后台");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private BusinessException locked(AdminLoginRateLimiter.LimitState state) {
        return new BusinessException(429,
                "密码错误已达 5 次，账号已临时锁定，请约 " + state.retryAfterMinutes() + " 分钟后再试");
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = String.valueOf(left).getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = String.valueOf(right).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }
}
