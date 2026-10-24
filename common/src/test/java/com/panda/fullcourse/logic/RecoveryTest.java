package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Recovery: the delay, the interval and Mending's table. */
class RecoveryTest {
    @Test
    void intervalTableFromTheMdd() {
        assertEquals(80, Recovery.interval(80, 0));
        assertEquals(40, Recovery.interval(80, 2));
        assertEquals(27, Recovery.interval(80, 4));
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
