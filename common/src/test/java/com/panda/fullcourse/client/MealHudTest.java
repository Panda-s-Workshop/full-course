package com.panda.fullcourse.client;

import static org.junit.jupiter.api.Assertions.*;

import com.panda.fullcourse.logic.Tier;
import org.junit.jupiter.api.Test;

/** A meal's ring on a blink: every pixel flips, the drained track too, so a nearly spent meal still flashes. */
class MealHudTest {
    private static int brightness(int argb) {
        return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
    }

    @Test
    void theWholeRingBlinks() {
        for (Tier tier : Tier.values()) {
            for (boolean lit : new boolean[] {true, false}) {
                assertEquals(MealHud.BLINK_COLOR, MealHud.ringColor(tier, lit, true), tier + (lit ? " lit" : " drained") + " pixel on the off beat");
            }
            assertEquals(tier.color(), MealHud.ringColor(tier, true, false));
            assertEquals(tier.trackColor(), MealHud.ringColor(tier, false, false));
            // the drained part, most of a ring about to end, changes by far more than the light iron's lit part would
            assertTrue(brightness(MealHud.BLINK_COLOR) - brightness(tier.trackColor()) > 300, tier + " track against the blink");
        }
    }
}
