package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The dim track under a draining meal ring still tells the tiers apart and stays clear of the frame's grey. */
class TierTrackTest {
    private static int ch(int argb, int shift) {
        return (argb >> shift) & 0xFF;
    }

    private static int brightness(int argb) {
        return ch(argb, 16) + ch(argb, 8) + ch(argb, 0);
    }

    @Test
    void trackIsDarkerThanTheTierButNotTheFrame() {
        for (Tier tier : Tier.values()) {
            int track = tier.trackColor();
            assertEquals(0xFF, track >>> 24, tier + " track is opaque");
            assertTrue(brightness(track) < brightness(tier.color()), tier + " track darker than its colour");
            assertTrue(brightness(track) > brightness(Tier.FRAME_GREY) + 30, tier + " track lighter than the frame");
        }
    }

    @Test
    void trackKeepsTheTiersApart() {
        Set<Integer> seen = new HashSet<>();
        for (Tier tier : Tier.values()) assertTrue(seen.add(tier.trackColor()), tier + " track repeats another tier's");
        // the strongest channel stays the strongest, so gold stays gold and amethyst stays amethyst
        for (Tier tier : Tier.values()) {
            int c = tier.color(), t = tier.trackColor();
            for (int a : new int[] {0, 8, 16}) {
                for (int b : new int[] {0, 8, 16}) {
                    if (ch(c, a) > ch(c, b)) assertTrue(ch(t, a) >= ch(t, b), tier + " channel order");
                }
            }
        }
    }
}
