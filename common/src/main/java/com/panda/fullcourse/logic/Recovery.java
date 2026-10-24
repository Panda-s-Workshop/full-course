package com.panda.fullcourse.logic;

/**
 * Recovery replaces natural regeneration: after a delay without damage, 1 HP every interval; Mending
 * shortens the interval. Food heals over time on top: {@code nutrition} HP, 1 HP a second, the larger heal wins.
 */
public final class Recovery {
    /** Food heals 1 HP every this many ticks. */
    public static final int NOURISH_TICKS = 20;

    private Recovery() {}

    /** Ticks between Recovery heals at a Mending level (0 = none). */
    public static int interval(int baseTicks, int mendingLevel) {
        return Math.max(1, Math.round(baseTicks / (1F + 0.5F * Math.max(0, mendingLevel))));
    }

    /**
     * Whether Recovery heals on this tick: once the delay after the last damage is over, then every {@code interval}.
     *
     * @param sinceDamage ticks since the player last took damage
     */
    public static boolean healsNow(long sinceDamage, int delayTicks, int interval) {
        long since = sinceDamage - delayTicks;
        return since >= 0 && since % interval == 0;
    }

    /** The heal-over-time left after eating food with {@code nutrition}: a new heal replaces a smaller one. */
    public static int nourish(int remaining, int nutrition) {
        return Math.max(remaining, Math.max(0, nutrition));
    }
}
