package com.tongluxing.invite.support;

import java.time.LocalDateTime;
import java.util.Locale;

/** 邀请绑定的纯规则：邀请码规范化与七天边界。 */
public final class InviteBindingRules {
    private InviteBindingRules() {
    }

    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isWithinWindow(LocalDateTime registeredAt,
                                         LocalDateTime now, long days) {
        return registeredAt != null && now != null
                && now.isBefore(registeredAt.plusDays(days));
    }
}
