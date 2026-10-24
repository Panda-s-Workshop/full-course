package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Slot milestones: thresholds, order, the feast condition and what the next one asks for. */
class MilestoneTest {
    private static Milestones.Count count(int prepared, int hearty, boolean feast) {
        return new Milestones.Count(prepared, hearty, feast);
    }

    @Test
    void thirdSlotAtEightPreparedDishes() {
        assertEquals(0, Milestones.reached(count(7, 7, true)));
        assertEquals(1, Milestones.reached(count(8, 0, false)));
    }

    @Test
    void fourthSlotAtFifteenHeartyDishes() {
        assertEquals(1, Milestones.reached(count(30, 14, true)));
        assertEquals(2, Milestones.reached(count(30, 15, false)));
    }

    @Test
    void fifthSlotNeedsTwentyHeartyAndAFeast() {
        assertEquals(2, Milestones.reached(count(30, 20, false)));
        assertEquals(2, Milestones.reached(count(30, 19, true)));
        assertEquals(3, Milestones.reached(count(30, 20, true)));
    }

    @Test
    void milestonesComeInOrder() {
        // hearty dishes are prepared dishes too, but a count that skips the third slot's can't reach the fourth
        assertEquals(0, Milestones.reached(count(5, 25, true)));
    }

    @Test
    void nextSaysWhatIsLeft() {
        assertEquals(new Milestones.Next(3, 2, 5, 8, false), Milestones.next(count(5, 2, false)));
        assertEquals(new Milestones.Next(4, 3, 9, 15, false), Milestones.next(count(12, 9, false)));
        assertEquals(new Milestones.Next(5, 3, 20, 20, true), Milestones.next(count(25, 20, false)));
        assertEquals(Milestones.Next.NONE, Milestones.next(count(25, 21, true)));
    }

    @Test
    void theFifthSlotIsReachableInABaseInstall() {
        // 21 of the mod's dishes have three stars or more, plus rabbit stew and cake
        assertTrue(Milestones.FIFTH <= 21 + 2);
    }
}
