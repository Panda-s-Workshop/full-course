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
    void nextSkipsSlotsAlreadyEarned() {
        // a command gave the third slot, or a datapack rated dishes lower after it was earned
        assertEquals(new Milestones.Next(4, 3, 1, 15, false), Milestones.next(count(3, 1, false), 1));
        assertEquals(Milestones.Next.NONE, Milestones.next(count(0, 0, false), 3));
        assertEquals(Milestones.next(count(12, 9, false)), Milestones.next(count(12, 9, false), 0));
    }

    @Test
    void countsMarksDishesThatMoveTheNextSlot() {
        Milestones.Next third = Milestones.next(count(5, 2, false));
        assertTrue(Milestones.counts(third, Tier.PREPARED));
        assertTrue(Milestones.counts(third, Tier.FEAST));
        assertFalse(Milestones.counts(third, Tier.SIMPLE));
        Milestones.Next fourth = Milestones.next(count(9, 4, false));
        assertFalse(Milestones.counts(fourth, Tier.PREPARED));
        assertTrue(Milestones.counts(fourth, Tier.HEARTY));
        // twenty hearty dishes but no feast: only a feast still helps
        Milestones.Next fifth = Milestones.next(count(25, 22, false));
        assertFalse(Milestones.counts(fifth, Tier.GOURMET));
        assertTrue(Milestones.counts(fifth, Tier.FEAST));
        assertFalse(Milestones.counts(Milestones.next(count(25, 22, true)), Tier.FEAST));
    }

    @Test
    void countsAgreesWithWhatTastingDoes() {
        for (int prepared = 0; prepared <= 24; prepared++) {
            for (int hearty = 0; hearty <= prepared; hearty++) {
                for (boolean feast : new boolean[] {false, true}) {
                    if (feast && hearty == 0) continue;
                    Milestones.Count before = count(prepared, hearty, feast);
                    Milestones.Next next = Milestones.next(before);
                    for (Tier tier : Tier.values()) {
                        Milestones.Count after = count(prepared + (tier.stars() >= 2 ? 1 : 0), hearty + (tier.stars() >= 3 ? 1 : 0),
                                feast || tier == Tier.FEAST);
                        Milestones.Next then = Milestones.next(after);
                        boolean moved = Milestones.reached(after) > Milestones.reached(before)
                                || Math.min(then.have(), then.need()) > Math.min(next.have(), next.need())
                                || next.needsFeast() && !then.needsFeast();
                        assertEquals(moved, Milestones.counts(next, tier), before + " + a " + tier + " dish");
                    }
                }
            }
        }
    }
}
