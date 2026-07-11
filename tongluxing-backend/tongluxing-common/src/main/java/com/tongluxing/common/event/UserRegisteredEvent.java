package com.tongluxing.common.event;

import java.time.LocalDateTime;

import org.springframework.util.StringUtils;

/**
 * 用户首次注册成功事件。
 *
 * <p>该事件只表示新用户账号创建成功。已有用户登录不发布该事件，业务模块只能基于
 * sourceType/sourceCode 自行处理本模块负责的来源关系。</p>
 */
public record UserRegisteredEvent(
        Long userId,
        String sourceType,
        String sourceCode,
        LocalDateTime registerTime
) {

    public boolean hasSource(String expectedType) {
        return StringUtils.hasText(sourceType)
                && StringUtils.hasText(sourceCode)
                && sourceType.trim().equalsIgnoreCase(expectedType);
    }

    public String normalizedSourceCode() {
        return sourceCode == null ? "" : sourceCode.trim();
    }
}
