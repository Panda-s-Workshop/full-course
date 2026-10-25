package com.panda.fullcourse.client;

import java.util.Properties;

/**
 * Each player's own display settings, never synced: {@code mealRowOffset} lifts the meal row (and what stacks above it)
 * to make room for another mod's HUD row, and {@code showRecoveryParticles} shows a heart on each Recovery heal. NeoForge
 * reads them from its client config, Fabric from {@code config/fullcourse-client.properties} ({@link #fromProperties}).
 */
public final class ClientOptions {
    /** The most a row may be lifted, in GUI pixels. */
    public static final int MAX_OFFSET = 200;

    public interface Source {
        int mealRowOffset();

        boolean showRecoveryParticles();
    }

    public record Values(int mealRowOffset, boolean showRecoveryParticles) implements Source {}

    public static final Values DEFAULTS = new Values(0, false);

    /** The comment written at the top of a new Fabric file. */
    public static final String HEADER = """
             Full Course client settings (read once at startup: restart after editing)
             mealRowOffset: GUI pixels to lift the meal row by, to make room for another mod's HUD row (0-200)
             showRecoveryParticles: show a heart each time Recovery heals you (true/false)""";

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
        boolean show = DEFAULTS.showRecoveryParticles();
        String hearts = p.getProperty("showRecoveryParticles");
        if (hearts != null && hearts.trim().equalsIgnoreCase("true")) show = true;
        if (hearts != null && hearts.trim().equalsIgnoreCase("false")) show = false;
        return new Values(offset, show);
    }

    /** The defaults, as a new Fabric file holds them. */
    public static Properties defaults() {
        Properties p = new Properties();
        p.setProperty("mealRowOffset", String.valueOf(DEFAULTS.mealRowOffset()));
        p.setProperty("showRecoveryParticles", String.valueOf(DEFAULTS.showRecoveryParticles()));
        return p;
    }
}
