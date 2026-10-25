package com.panda.fullcourse;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * The settings read from a {@code .properties} file (Fabric's {@code config/fullcourse.properties}). A missing or
 * unreadable key takes its default; numbers are clamped to the same ranges NeoForge's config allows.
 */
public final class PropertiesConfig implements FullCourseConfig.Source {
    /** The comment written at the top of a new file. */
    public static final String HEADER = """
             Full Course (read once at startup: restart after editing)
             keepVanillaHunger: keep vanilla hunger and regeneration; Full Course only adds meal benefits (true/false)
             recoveryDelaySeconds: seconds without taking damage before Recovery starts healing (0-600)
             recoveryIntervalTicks: ticks between Recovery heals of 1 HP, before Mending (0-12000; 0 turns Recovery off)
             startingMealSlots: meal slots a new player has (1-16)
             maxMealSlots: the most meal slots a player can have (1-16); the journal opens three past the starting slots
             keepMealsOnDeath: keep held meals through death (true/false)
             durationMultiplier: multiplies every meal's duration (0.05-20)
             tierThresholds: lowest recipe-chain score of tiers 1 to 5, exactly five, each higher than the last, comma-separated
             slotMilestones: different dishes to taste for meal slot 3 (two stars or more), 4 and 5 (three stars or more; 5 also a feast), comma-separated""";

    private final boolean keepVanillaHunger;
    private final int recoveryDelaySeconds;
    private final int recoveryIntervalTicks;
    private final int startingMealSlots;
    private final int maxMealSlots;
    private final boolean keepMealsOnDeath;
    private final double durationMultiplier;
    private final int[] tierThresholds;
    private final int[] slotMilestones;

    public PropertiesConfig(Properties p) {
        FullCourseConfig.Source d = FullCourseConfig.DEFAULTS;
        keepVanillaHunger = bool(p.getProperty("keepVanillaHunger"), d.keepVanillaHunger());
        recoveryDelaySeconds = clamp(p.getProperty("recoveryDelaySeconds"), d.recoveryDelaySeconds(), 0, 600);
        recoveryIntervalTicks = clamp(p.getProperty("recoveryIntervalTicks"), d.recoveryIntervalTicks(), 0, 12000);
        startingMealSlots = clamp(p.getProperty("startingMealSlots"), d.startingMealSlots(), 1, 16);
        maxMealSlots = clamp(p.getProperty("maxMealSlots"), d.maxMealSlots(), 1, 16);
        keepMealsOnDeath = bool(p.getProperty("keepMealsOnDeath"), d.keepMealsOnDeath());
        durationMultiplier = clamp(p.getProperty("durationMultiplier"), d.durationMultiplier());
        tierThresholds = FullCourseConfig.validThresholds(thresholds(p.getProperty("tierThresholds")));
        slotMilestones = FullCourseConfig.validMilestones(thresholds(p.getProperty("slotMilestones")));
    }

    /** The defaults, as a new file holds them. */
    public static Properties defaults() {
        FullCourseConfig.Source d = FullCourseConfig.DEFAULTS;
        Properties p = new Properties();
        p.setProperty("keepVanillaHunger", String.valueOf(d.keepVanillaHunger()));
        p.setProperty("recoveryDelaySeconds", String.valueOf(d.recoveryDelaySeconds()));
        p.setProperty("recoveryIntervalTicks", String.valueOf(d.recoveryIntervalTicks()));
        p.setProperty("startingMealSlots", String.valueOf(d.startingMealSlots()));
        p.setProperty("maxMealSlots", String.valueOf(d.maxMealSlots()));
        p.setProperty("keepMealsOnDeath", String.valueOf(d.keepMealsOnDeath()));
        p.setProperty("durationMultiplier", String.valueOf(d.durationMultiplier()));
        p.setProperty("tierThresholds", Arrays.stream(d.tierThresholds()).mapToObj(String::valueOf).collect(Collectors.joining(",")));
        p.setProperty("slotMilestones", Arrays.stream(d.slotMilestones()).mapToObj(String::valueOf).collect(Collectors.joining(",")));
        return p;
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null) return fallback;
        String v = value.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return fallback;
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
            double v = Double.parseDouble(value.trim());
            return Double.isFinite(v) ? Math.clamp(v, 0.05, 20.0) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int[] thresholds(String value) {
        if (value == null) return null;
        try {
            return Arrays.stream(value.split(",")).mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        } catch (NumberFormatException e) {
            return null;
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
    @Override public int[] slotMilestones() { return slotMilestones.clone(); }
}
