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
        // SecurityContext 是网关/JWT 过滤器完成认证后保存主体信息的统一位置。
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // 未建立认证对象或认证尚未通过时，业务层不能继续访问任何“当前用户”数据。
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            // 部分内部认证链会直接把平台用户 ID 作为 principal，直接返回即可。
            return userId;
        }
        if (principal instanceof String text) {
            try {
                // JWT 过滤器也可能把 subject 以字符串保存，这里统一转换为 Long。
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // "anonymousUser" 或损坏的 subject 都不能被当作合法用户 ID。
                throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态无效");
            }
        }

        try {
            // 兼容 auth-module 定义的 record/认证主体，避免 user-module 对其具体类型产生依赖。
            Method method = principal.getClass().getMethod("userId");
            Object value = method.invoke(principal);
            if (value instanceof Long userId) {
                // 只接受 Long，防止任意方法返回值被静默转换后绕过认证约定。
                return userId;
            }
        } catch (ReflectiveOperationException exception) {
            // principal 不符合约定时，不向外暴露内部类型细节，只提示登录状态无效。
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态无效");
        }

        // 存在 userId() 但返回类型不符合约定，同样视为认证上下文不可用。
        throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态无效");
    }
}
