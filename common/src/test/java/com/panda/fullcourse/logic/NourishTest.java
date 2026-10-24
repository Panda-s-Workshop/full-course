package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Food heals over time: a new heal replaces the running one only if it is larger. */
class NourishTest {
    @Test
    void largerHealWinsAndHealsDontStack() {
        assertEquals(8, Recovery.nourish(0, 8));
        assertEquals(8, Recovery.nourish(8, 3));
        assertEquals(6, Recovery.nourish(2, 6));
        assertEquals(0, Recovery.nourish(0, -1), "no negative heal");
    }

    @Test
    void oneHpASecond() {
        assertEquals(20, Recovery.NOURISH_TICKS);
    }
}
