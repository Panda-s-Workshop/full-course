package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShortHalvesTest {
    /** As a data slot carries it: cut to a short and read back sign-extended. */
    private static int wire(int value) {
        return (short) value;
    }

    @Test
    void anItemIdSurvivesTheShortDataSlots() {
        for (int id : new int[] {-1, 0, 1, 32766, 32767, 32768, 40000, 65535, 70000, 1_000_000}) {
            assertEquals(id, ShortHalves.join(wire(ShortHalves.low(id)), wire(ShortHalves.high(id))), "id " + id);
        }
    }
}
