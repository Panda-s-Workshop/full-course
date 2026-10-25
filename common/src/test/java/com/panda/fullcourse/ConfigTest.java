package com.panda.fullcourse;

import static org.junit.jupiter.api.Assertions.*;

import com.panda.fullcourse.logic.Tier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** The config values a server sends, and the Fabric file's parse. */
class ConfigTest {
    private static FullCourseConfig.Snapshot roundTrip(FullCourseConfig.Snapshot s) {
        ByteBuf buf = Unpooled.buffer();
        FullCourseConfig.Snapshot.STREAM_CODEC.encode(buf, s);
        FullCourseConfig.Snapshot back = FullCourseConfig.Snapshot.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the codec should read everything it wrote");
        return back;
    }

    private static void assertSame(FullCourseConfig.Source a, FullCourseConfig.Source b) {
        assertEquals(a.keepVanillaHunger(), b.keepVanillaHunger());
        assertEquals(a.recoveryDelaySeconds(), b.recoveryDelaySeconds());
        assertEquals(a.recoveryIntervalTicks(), b.recoveryIntervalTicks());
        assertEquals(a.startingMealSlots(), b.startingMealSlots());
        assertEquals(a.maxMealSlots(), b.maxMealSlots());
        assertEquals(a.keepMealsOnDeath(), b.keepMealsOnDeath());
        assertEquals(a.durationMultiplier(), b.durationMultiplier());
        assertArrayEquals(a.tierThresholds(), b.tierThresholds());
    }

    @Test
    void snapshotSurvivesTheWire() {
        var s = new FullCourseConfig.Snapshot(true, 3, 40, 1, 9, true, 2.5, new int[] {0, 2, 4, 9, 30});
        assertSame(s, roundTrip(s));
        assertSame(FullCourseConfig.DEFAULTS, roundTrip(FullCourseConfig.Snapshot.of(FullCourseConfig.DEFAULTS)));
    }

    @Test
    void snapshotSendsTheThresholdsTheServerUses() {
        var six = new FullCourseConfig.Snapshot(false, 8, 80, 2, 5, false, 1.0, new int[] {1, 2, 3, 4, 5, 6});
        assertArrayEquals(Tier.DEFAULT_THRESHOLDS, FullCourseConfig.Snapshot.of(six).tierThresholds());
        var falling = new FullCourseConfig.Snapshot(false, 8, 80, 2, 5, false, 1.0, new int[] {5, 4, 3, 2, 1});
        assertArrayEquals(Tier.DEFAULT_THRESHOLDS, roundTrip(FullCourseConfig.Snapshot.of(falling)).tierThresholds());
        // score 0 is raw food: a Simple threshold of 0 would make rotten flesh a meal
        var zero = new FullCourseConfig.Snapshot(false, 8, 80, 2, 5, false, 1.0, new int[] {0, 3, 5, 8, 12});
        assertArrayEquals(Tier.DEFAULT_THRESHOLDS, FullCourseConfig.Snapshot.of(zero).tierThresholds());
        // two equal thresholds leave a tier no food can reach
        var equal = new FullCourseConfig.Snapshot(false, 8, 80, 2, 5, false, 1.0, new int[] {1, 3, 3, 8, 12});
        assertArrayEquals(Tier.DEFAULT_THRESHOLDS, FullCourseConfig.Snapshot.of(equal).tierThresholds());
    }

    @Test
    void anEmptyFileGivesTheDefaults() {
        assertSame(FullCourseConfig.DEFAULTS, new PropertiesConfig(new Properties()));
        assertSame(FullCourseConfig.DEFAULTS, new PropertiesConfig(PropertiesConfig.defaults()));
    }

    @Test
    void fileValuesAreReadClampedAndCheckedAsNeoForgeDoes() {
        Properties p = new Properties();
        p.setProperty("keepVanillaHunger", " TRUE ");
        p.setProperty("recoveryDelaySeconds", "9999");
        p.setProperty("recoveryIntervalTicks", "-5");
        p.setProperty("startingMealSlots", "3");
        p.setProperty("maxMealSlots", "seven");
        p.setProperty("keepMealsOnDeath", "yes");
        p.setProperty("durationMultiplier", "NaN");
        p.setProperty("tierThresholds", "2, 4, 6, 10, 14");
        var c = new PropertiesConfig(p);
        assertTrue(c.keepVanillaHunger());
        assertEquals(600, c.recoveryDelaySeconds());
        assertEquals(0, c.recoveryIntervalTicks(), "clamped to 0, which turns Recovery off");
        assertEquals(3, c.startingMealSlots());
        assertEquals(FullCourseConfig.DEFAULTS.maxMealSlots(), c.maxMealSlots());
        assertFalse(c.keepMealsOnDeath(), "a word other than true or false keeps the default");
        assertEquals(1.0, c.durationMultiplier());
        assertArrayEquals(new int[] {2, 4, 6, 10, 14}, c.tierThresholds());

        p.setProperty("durationMultiplier", "100");
        p.setProperty("tierThresholds", "1,3,5,8");
        c = new PropertiesConfig(p);
        assertEquals(20.0, c.durationMultiplier());
        assertArrayEquals(Tier.DEFAULT_THRESHOLDS, c.tierThresholds(), "four thresholds aren't five");
        p.setProperty("tierThresholds", "1,3,x,8,12");
        assertArrayEquals(Tier.DEFAULT_THRESHOLDS, new PropertiesConfig(p).tierThresholds());
    }

    @Test
    void anOlderFileGainsTheKeysItLacks() throws java.io.IOException {
        Properties old = new Properties();
        old.setProperty("keepVanillaHunger", "true");
        String missing = PropertiesConfig.missingLines(old, PropertiesConfig.defaults(), PropertiesConfig.HEADER);
        assertFalse(missing.contains("keepVanillaHunger"), "a key the file has stays as the user wrote it");
        assertTrue(missing.contains("# slotMilestones: different dishes to taste"), "each added key brings its comment: " + missing);
        Properties added = new Properties();
        added.load(new java.io.StringReader(missing));
        assertEquals("8,15,20", added.getProperty("slotMilestones"));
        assertEquals(PropertiesConfig.defaults().size() - 1, added.size());
        assertEquals("", PropertiesConfig.missingLines(PropertiesConfig.defaults(), PropertiesConfig.defaults(), PropertiesConfig.HEADER));
    }
}
