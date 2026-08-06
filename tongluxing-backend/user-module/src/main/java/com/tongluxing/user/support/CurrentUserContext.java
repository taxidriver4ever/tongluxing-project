package com.tongluxing.user.support;

import java.lang.reflect.Method;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;

/**
 * 当前登录用户上下文工具。
 *
 * <p>统一从 Spring Security 的 {@link SecurityContextHolder} 中解析用户 ID。
 * 业务代码通过该组件获取当前用户，避免在各个模块里重复解析 Authentication。</p>
 */
@Component
public class CurrentUserContext {

    /**
     * 获取当前登录用户 ID。
     *
     * <p>兼容多种 principal 形态：直接 Long、字符串形式的用户 ID，
     * 或包含 userId() 方法的认证主体对象。解析失败时统一抛出未登录/登录状态无效异常。</p>
     */
    public Long requireUserId() {
        Long userId = getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    /**
     * 尝试获取当前登录用户 ID；公开接口允许匿名访问时返回 {@code null}，
     * 不再把 Spring Security 的 anonymousUser 误判成损坏登录态。
     */
    public Long getUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        try {
            Method method = principal.getClass().getMethod("userId");
            Object value = method.invoke(principal);
            return value instanceof Long userId ? userId : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
