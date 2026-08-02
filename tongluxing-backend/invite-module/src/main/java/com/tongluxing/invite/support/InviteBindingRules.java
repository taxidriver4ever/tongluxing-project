package com.tongluxing.invite.support;

import java.time.LocalDateTime;
import java.util.Locale;

/** 邀请绑定的纯规则：邀请码规范化与七天边界。 */
public final class InviteBindingRules {
    /** 纯工具类不允许实例化。 */
    private InviteBindingRules() {
    }

    /** 去除邀请码首尾空白并使用 ROOT Locale 转大写，避免服务器语言差异。 */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 判断当前时间是否严格早于“注册时间 + 窗口天数”。
     * 恰好等于过期时刻时已不允许绑定。
     */
    public static boolean isWithinWindow(LocalDateTime registeredAt,
                                         LocalDateTime now, long days) {
        return registeredAt != null && now != null
                && now.isBefore(registeredAt.plusDays(days));
    }
}
