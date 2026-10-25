package com.panda.fullcourse.client;

import java.util.Locale;
import java.util.Properties;

/**
 * Each player's own display settings, never synced. {@code mealRowAnchor} and {@code mealRowX}/{@code mealRowY} place
 * the meal row (on the hotbar's stack where the food bar was, or at a corner or edge of the screen) so it can keep
 * clear of other mods' HUDs; {@code mealList} and {@code mealListX}/{@code mealListY} do the same for the meal list
 * beside the inventory. {@code showRecoveryParticles} shows a heart on each Recovery heal, and {@code showTierPips}
 * marks every meal food's icon with its tier's colour. NeoForge reads them from its client config, Fabric from
 * {@code config/fullcourse-client.properties} ({@link #fromProperties}).
 */
public final class ClientOptions {
    /** The most a row or list may be shifted either way, in GUI pixels; it is kept on screen whatever the shift. */
    public static final int MAX_SHIFT = 2000;

    /** Where the meal row sits: on the hotbar's stack (the food bar's place), or at a corner or edge of the screen. */
    public enum Anchor {
        HOTBAR, TOP_LEFT, TOP, TOP_RIGHT, LEFT, RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        /** The name in a settings file: {@code top_left}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Which side of the inventory the meal list sits on, or {@code OFF} to hide it. */
    public enum ListSide {
        LEFT, RIGHT, OFF;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public interface Source {
        Anchor mealRowAnchor();

        int mealRowX();

        int mealRowY();

        ListSide mealList();

        int mealListX();

        int mealListY();

        boolean showRecoveryParticles();

        boolean showTierPips();
    }

    public record Values(Anchor mealRowAnchor, int mealRowX, int mealRowY, ListSide mealList, int mealListX, int mealListY,
                         boolean showRecoveryParticles, boolean showTierPips) implements Source {
        public Values withMealRow(Anchor anchor, int x, int y) {
            return new Values(anchor, x, y, mealList, mealListX, mealListY, showRecoveryParticles, showTierPips);
        }

        public Values withMealList(ListSide side, int x, int y) {
            return new Values(mealRowAnchor, mealRowX, mealRowY, side, x, y, showRecoveryParticles, showTierPips);
        }

        public Values withRecoveryParticles(boolean on) {
            return new Values(mealRowAnchor, mealRowX, mealRowY, mealList, mealListX, mealListY, on, showTierPips);
        }
    }

    public static final Values DEFAULTS = new Values(Anchor.HOTBAR, 0, 0, ListSide.LEFT, 0, 0, false, true);

    /** The comment written at the top of a new Fabric file. */
    public static final String HEADER = """
             Full Course client settings (read once at startup: restart after editing)
             mealRowAnchor: where the meal row sits: hotbar (where the food bar was, with the rows above it lifted
               to make room), top_left, top, top_right, left, right, bottom_left or bottom_right
             mealRowX, mealRowY: GUI pixels to move the meal row from there, + right and down (-2000 to 2000; it stays
               on screen). On the hotbar, a negative mealRowY lifts the row and everything stacked above it
             mealList: where the meal list sits beside the inventory: left, right or off
             mealListX, mealListY: GUI pixels to move the meal list from there, + right and down (-2000 to 2000)
             showRecoveryParticles: show a heart each time Recovery heals you (true/false)
             showTierPips: a dot in the tier's colour on the icon of every food that gives a meal (true/false)""";

    private static Source source = DEFAULTS;

    private ClientOptions() {}

    public static void use(Source loaderOptions) {
        source = loaderOptions;
    }

    public static Anchor mealRowAnchor() {
        Anchor a = source.mealRowAnchor();
        return a == null ? DEFAULTS.mealRowAnchor() : a;
    }

    public static int mealRowX() {
        return Math.clamp(source.mealRowX(), -MAX_SHIFT, MAX_SHIFT);
    }

    public static int mealRowY() {
        return Math.clamp(source.mealRowY(), -MAX_SHIFT, MAX_SHIFT);
    }

    public static ListSide mealList() {
        ListSide s = source.mealList();
        return s == null ? DEFAULTS.mealList() : s;
    }

    public static int mealListX() {
        return Math.clamp(source.mealListX(), -MAX_SHIFT, MAX_SHIFT);
    }

    public static int mealListY() {
        return Math.clamp(source.mealListY(), -MAX_SHIFT, MAX_SHIFT);
    }

    public static boolean showRecoveryParticles() {
        return source.showRecoveryParticles();
    }

    public static boolean showTierPips() {
        return source.showTierPips();
    }

    /** Values from a {@code .properties} file: a missing or unreadable key takes its default, shifts are clamped. */
    public static Values fromProperties(Properties p) {
        return new Values(choice(Anchor.class, p.getProperty("mealRowAnchor"), DEFAULTS.mealRowAnchor()),
                shift(p.getProperty("mealRowX"), DEFAULTS.mealRowX()), shift(p.getProperty("mealRowY"), DEFAULTS.mealRowY()),
                choice(ListSide.class, p.getProperty("mealList"), DEFAULTS.mealList()),
                shift(p.getProperty("mealListX"), DEFAULTS.mealListX()), shift(p.getProperty("mealListY"), DEFAULTS.mealListY()),
                bool(p.getProperty("showRecoveryParticles"), DEFAULTS.showRecoveryParticles()),
                bool(p.getProperty("showTierPips"), DEFAULTS.showTierPips()));
    }

    private static int shift(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Math.clamp(Integer.parseInt(value.trim()), -MAX_SHIFT, MAX_SHIFT);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E choice(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        for (E e : type.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(value.trim().replace('-', '_'))) return e;
        }
        return fallback;
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
        p.setProperty("mealRowAnchor", DEFAULTS.mealRowAnchor().key());
        p.setProperty("mealRowX", String.valueOf(DEFAULTS.mealRowX()));
        p.setProperty("mealRowY", String.valueOf(DEFAULTS.mealRowY()));
        p.setProperty("mealList", DEFAULTS.mealList().key());
        p.setProperty("mealListX", String.valueOf(DEFAULTS.mealListX()));
        p.setProperty("mealListY", String.valueOf(DEFAULTS.mealListY()));
        p.setProperty("showRecoveryParticles", String.valueOf(DEFAULTS.showRecoveryParticles()));
        p.setProperty("showTierPips", String.valueOf(DEFAULTS.showTierPips()));
        return p;
    }
}
