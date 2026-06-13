package com.tongdao.user.support;

import java.lang.reflect.Method;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;

@Component
public class CurrentUserContext {

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
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态无效");
        }

        throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态无效");
    }
}
