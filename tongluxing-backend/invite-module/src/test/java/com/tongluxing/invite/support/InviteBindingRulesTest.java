package com.tongluxing.invite.support;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InviteBindingRulesTest {

    @Test
    void normalizesWhitespaceAndCase() {
        assertEquals("ABC123", InviteBindingRules.normalizeCode("  abc123  "));
    }

    @Test
    void sevenDayBoundaryIsExclusive() {
        LocalDateTime registeredAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        assertTrue(InviteBindingRules.isWithinWindow(
                registeredAt, registeredAt.plusDays(7).minusNanos(1), 7));
        assertFalse(InviteBindingRules.isWithinWindow(
                registeredAt, registeredAt.plusDays(7), 7));
    }
}
