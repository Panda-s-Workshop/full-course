package com.panda.fullcourse.client;

import java.util.Properties;

/**
 * Each player's own display settings, never synced: {@code mealRowOffset} lifts the meal row (and what stacks above it)
 * to make room for another mod's HUD row, {@code showRecoveryParticles} shows a heart on each Recovery heal, and
 * {@code showTierPips} marks every meal food's icon with its tier's colour. NeoForge
 * reads them from its client config, Fabric from {@code config/fullcourse-client.properties} ({@link #fromProperties}).
 */
public final class ClientOptions {
    /** The most a row may be lifted, in GUI pixels. */
    public static final int MAX_OFFSET = 200;

    public interface Source {
        int mealRowOffset();

        boolean showRecoveryParticles();

        boolean showTierPips();
    }

    public record Values(int mealRowOffset, boolean showRecoveryParticles, boolean showTierPips) implements Source {}

    public static final Values DEFAULTS = new Values(0, false, true);

    /** The comment written at the top of a new Fabric file. */
    public static final String HEADER = """
             Full Course client settings (read once at startup: restart after editing)
             mealRowOffset: GUI pixels to lift the meal row by, to make room for another mod's HUD row (0-200)
             showRecoveryParticles: show a heart each time Recovery heals you (true/false)
             showTierPips: a dot in the tier's colour on the icon of every food that gives a meal (true/false)""";

    private static Source source = DEFAULTS;

    private ClientOptions() {}

    public static void use(Source loaderOptions) {
        source = loaderOptions;
    }

    public static int mealRowOffset() {
        return Math.clamp(source.mealRowOffset(), 0, MAX_OFFSET);
    }

    public static boolean showRecoveryParticles() {
        return source.showRecoveryParticles();
    }

    public static boolean showTierPips() {
        return source.showTierPips();
    }

    /** Values from a {@code .properties} file: a missing or unreadable key takes its default, the offset is clamped. */
    public static Values fromProperties(Properties p) {
        int offset = DEFAULTS.mealRowOffset();
        String raw = p.getProperty("mealRowOffset");
        if (raw != null) {
            try {
                offset = Math.clamp(Integer.parseInt(raw.trim()), 0, MAX_OFFSET);
            } catch (NumberFormatException e) {
                // keeps the default
            }
        }
        return new Values(offset, bool(p.getProperty("showRecoveryParticles"), DEFAULTS.showRecoveryParticles()),
                bool(p.getProperty("showTierPips"), DEFAULTS.showTierPips()));
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null) return fallback;
        if (value.trim().equalsIgnoreCase("true")) return true;
        if (value.trim().equalsIgnoreCase("false")) return false;
        return fallback;
    }

    /** The defaults, as a new Fabric file holds them. */
    public static Properties defaults() {
        Properties p = new Properties();
        p.setProperty("mealRowOffset", String.valueOf(DEFAULTS.mealRowOffset()));
        p.setProperty("showRecoveryParticles", String.valueOf(DEFAULTS.showRecoveryParticles()));
        p.setProperty("showTierPips", String.valueOf(DEFAULTS.showTierPips()));
        return p;
    }
}
