package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The balance targets that pure numbers can pin (the per-family caps are a server rule check). */
class BalanceTest {
    @Test
    void healingCaps() {
        // food: 1 HP per NOURISH_TICKS; Recovery at Mending IV: 1 HP per 27 ticks; Regeneration II: 1 HP per 25 ticks
        assertTrue(20.0 / Recovery.NOURISH_TICKS <= 1.0, "food heals at most 1 HP/s");
        assertTrue(20.0 / Recovery.interval(80, 4) <= 0.75, "Recovery at the best Mending stays at or under 0.75 HP/s");
        assertTrue(20.0 / Recovery.interval(80, 4) < 20.0 / 25, "and below Regeneration II");
    }

    @Test
    void tierCurve() {
        Tier[] t = Tier.values();
        for (int i = 2; i < t.length; i++) {
            assertTrue(t[i].benefitMinutes() >= 2 * t[i - 1].benefitMinutes(), t[i] + " vs " + t[i - 1]);
        }
        assertEquals(2, Tier.SIMPLE.benefitMinutes());
        assertEquals(10, Tier.PREPARED.benefitMinutes());
        assertEquals(40, Tier.HEARTY.benefitMinutes());
        assertEquals(180, Tier.GOURMET.benefitMinutes());
        assertEquals(360, Tier.FEAST.benefitMinutes());
    }
}
