package com.tongluxing.invite.support;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 邀请码规范化与七天绑定边界的纯规则单元测试。 */
class InviteBindingRulesTest {

    /** 验证前后空白被去除，且小写英文统一转为大写。 */
    @Test
    void normalizesWhitespaceAndCase() {
        assertEquals("ABC123", InviteBindingRules.normalizeCode("  abc123  "));
    }

    /** 验证过期时刻使用排他边界：前 1ns 有效，恰好七天时无效。 */
    @Test
    void sevenDayBoundaryIsExclusive() {
        LocalDateTime registeredAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        assertTrue(InviteBindingRules.isWithinWindow(
                registeredAt, registeredAt.plusDays(7).minusNanos(1), 7));
        assertFalse(InviteBindingRules.isWithinWindow(
                registeredAt, registeredAt.plusDays(7), 7));
    }
}
