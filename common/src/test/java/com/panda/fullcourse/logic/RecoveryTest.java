package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Recovery: the delay, the interval, Mending's table and the recovery rate. */
class RecoveryTest {
    @Test
    void intervalTableFromTheMdd() {
        assertEquals(80, Recovery.interval(80, 0));
        assertEquals(40, Recovery.interval(80, 2));
        assertEquals(27, Recovery.interval(80, 4));
    }

    @Test
    void theRateDividesTheInterval() {
        assertEquals(80, Recovery.interval(80, 1.0));
        assertEquals(Recovery.interval(80, 2), Recovery.interval(80, 2.0), "Mending II is a rate of 2");
        assertEquals(160, Recovery.interval(80, 0.5));
        assertEquals(5, Recovery.interval(80, 16.0));
    }

    @Test
    void noDelayStillWaitsOneIntervalAfterAHit() {
        assertFalse(Recovery.healsNow(0, 0, 80), "not on the tick of the hit");
        assertFalse(Recovery.healsNow(79, 0, 80));
        assertTrue(Recovery.healsNow(80, 0, 80));
        assertTrue(Recovery.healsNow(160, 0, 80));
    }

    /** A modded food can name any nutrition: the heal-over-time waiting stops at the cap. */
    @Test
    void nourishIsCapped() {
        assertEquals(Recovery.MAX_NOURISH, Recovery.nourish(0, 50_000));
        assertEquals(Recovery.MAX_NOURISH, Recovery.nourish(Recovery.MAX_NOURISH, 1));
        assertEquals(6, Recovery.nourish(2, 6), "a bigger heal replaces a smaller one");
        assertEquals(6, Recovery.nourish(6, 2), "a smaller one doesn't");
        assertEquals(3, Recovery.nourish(3, -4), "a negative nutrition adds nothing");
    }

    @Test
    void anIntervalOfZeroTurnsRecoveryOff() {
        assertEquals(0, Recovery.interval(0, 1.0));
        assertEquals(0, Recovery.interval(0, 4));
    }

    @Test
    void aRateOfZeroNeverHeals() {
        assertEquals(0, Recovery.interval(80, 0.0));
        for (long t = 0; t < 2000; t++) assertFalse(Recovery.healsNow(t, 160, 0));
    }

    @Test
    void firstHealWhenTheDelayEndsThenEveryInterval() {
        int delay = 160, interval = 80;
        int heals = 0;
        long firstHeal = -1, secondHeal = -1;
        for (long t = 0; t <= delay + 19L * interval; t++) {
            if (Recovery.healsNow(t, delay, interval)) {
                heals++;
                if (firstHeal < 0) firstHeal = t;
                else if (secondHeal < 0) secondHeal = t;
            }
        }
        assertEquals(delay, firstHeal, "after 8 s without damage");
        assertEquals(delay + interval, secondHeal);
        assertEquals(20, heals, "0 to 20 HP: 8 s, then 19 intervals of 4 s");
    }

    @Test
    void noHealDuringTheDelay() {
        for (long t = 0; t < 160; t++) assertFalse(Recovery.healsNow(t, 160, 80));
    }
}
