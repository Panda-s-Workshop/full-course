package com.panda.fullcourse.fabric;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's settings on Fabric: config/fullcourse.properties, written with the defaults on first start. Unlike
 * NeoForge's synced config it is read once at startup, on each side from its own file.
 */
final class FabricConfig implements FullCourseConfig.Source {
    private final boolean keepVanillaHunger;
    private final int recoveryDelaySeconds;
    private final int recoveryIntervalTicks;
    private final int startingMealSlots;
    private final int maxMealSlots;
    private final boolean keepMealsOnDeath;
    private final double durationMultiplier;
    private final int[] tierThresholds;

    private FabricConfig(Properties p) {
        FullCourseConfig.Source d = FullCourseConfig.DEFAULTS;
        keepVanillaHunger = Boolean.parseBoolean(p.getProperty("keepVanillaHunger", String.valueOf(d.keepVanillaHunger())));
        recoveryDelaySeconds = clamp(p.getProperty("recoveryDelaySeconds"), d.recoveryDelaySeconds(), 0, 600);
        recoveryIntervalTicks = clamp(p.getProperty("recoveryIntervalTicks"), d.recoveryIntervalTicks(), 1, 12000);
        startingMealSlots = clamp(p.getProperty("startingMealSlots"), d.startingMealSlots(), 1, 16);
        maxMealSlots = clamp(p.getProperty("maxMealSlots"), d.maxMealSlots(), 1, 16);
        keepMealsOnDeath = Boolean.parseBoolean(p.getProperty("keepMealsOnDeath", String.valueOf(d.keepMealsOnDeath())));
        durationMultiplier = clamp(p.getProperty("durationMultiplier"), d.durationMultiplier());
        tierThresholds = thresholds(p.getProperty("tierThresholds"), d.tierThresholds());
    }

    static FabricConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("fullcourse.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file)) {
                p.load(in);
            } catch (IOException e) {
                FullCourse.LOGGER.warn("Couldn't read {}, using the defaults", file, e);
            }
        } else {
            FullCourseConfig.Source d = FullCourseConfig.DEFAULTS;
            p.setProperty("keepVanillaHunger", String.valueOf(d.keepVanillaHunger()));
            p.setProperty("recoveryDelaySeconds", String.valueOf(d.recoveryDelaySeconds()));
            p.setProperty("recoveryIntervalTicks", String.valueOf(d.recoveryIntervalTicks()));
            p.setProperty("startingMealSlots", String.valueOf(d.startingMealSlots()));
            p.setProperty("maxMealSlots", String.valueOf(d.maxMealSlots()));
            p.setProperty("keepMealsOnDeath", String.valueOf(d.keepMealsOnDeath()));
            p.setProperty("durationMultiplier", String.valueOf(d.durationMultiplier()));
            p.setProperty("tierThresholds", Arrays.stream(d.tierThresholds()).mapToObj(String::valueOf).collect(Collectors.joining(",")));
            try (Writer out = Files.newBufferedWriter(file)) {
                p.store(out, """
                         Full Course
                         keepVanillaHunger: keep vanilla hunger and regeneration; Full Course only adds meal benefits (true/false)
                         recoveryDelaySeconds: seconds without taking damage before Recovery starts healing (0-600)
                         recoveryIntervalTicks: ticks between Recovery heals of 1 HP, before Mending (1-12000)
                         startingMealSlots: meal slots a new player has (1-16)
                         maxMealSlots: the most meal slots the Tasting Journal can unlock (1-16)
                         keepMealsOnDeath: keep held meals through death (true/false)
                         durationMultiplier: multiplies every meal's duration (0.05-20)
                         tierThresholds: lowest recipe-chain score of tiers 1 to 5, ascending, comma-separated""");
            } catch (IOException e) {
                FullCourse.LOGGER.warn("Couldn't write {}", file, e);
            }
        }
        return new FabricConfig(p);
    }

    private static int clamp(String value, int fallback, int min, int max) {
        if (value == null) return fallback;
        try {
            return Math.clamp(Integer.parseInt(value.trim()), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double clamp(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Math.clamp(Double.parseDouble(value.trim()), 0.05, 20.0);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int[] thresholds(String value, int[] fallback) {
        if (value == null) return fallback;
        try {
            return Arrays.stream(value.split(",")).mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override public boolean keepVanillaHunger() { return keepVanillaHunger; }
    @Override public int recoveryDelaySeconds() { return recoveryDelaySeconds; }
    @Override public int recoveryIntervalTicks() { return recoveryIntervalTicks; }
    @Override public int startingMealSlots() { return startingMealSlots; }
    @Override public int maxMealSlots() { return maxMealSlots; }
    @Override public boolean keepMealsOnDeath() { return keepMealsOnDeath; }
    @Override public double durationMultiplier() { return durationMultiplier; }
    @Override public int[] tierThresholds() { return tierThresholds.clone(); }
}
