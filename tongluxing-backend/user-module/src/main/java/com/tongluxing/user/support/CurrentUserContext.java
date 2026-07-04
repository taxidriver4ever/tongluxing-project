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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态无效");
            }
        }

        try {
            Method method = principal.getClass().getMethod("userId");
            Object value = method.invoke(principal);
            if (value instanceof Long userId) {
                return userId;
            }
        } catch (ReflectiveOperationException exception) {
            // principal 不符合约定时，不向外暴露内部类型细节，只提示登录状态无效。
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态无效");
        }

        throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态无效");
    }
}
