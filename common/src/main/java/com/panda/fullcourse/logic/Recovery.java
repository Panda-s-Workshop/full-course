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
        return interval(baseTicks, 1 + 0.5 * Math.max(0, mendingLevel));
    }

    /** The interval at a recovery rate ({@code fullcourse:recovery_rate}): the base divided by the rate, 0 for none. */
    public static int interval(int baseTicks, double rate) {
        return rate <= 0 ? 0 : (int) Math.max(1, Math.round(baseTicks / rate));
    }

    /**
     * Whether Recovery heals on this tick: once the delay after the last damage is over, then every {@code interval}.
     *
     * @param sinceDamage ticks since the player last took damage
     */
    public static boolean healsNow(long sinceDamage, int delayTicks, int interval) {
        if (interval <= 0) return false;
        long since = sinceDamage - delayTicks;
        return since >= 0 && since % interval == 0;
    }

    /** The most heal-over-time a player can have waiting, in HP (a modded food can name any nutrition). */
    public static final int MAX_NOURISH = 1000;

    /** The heal-over-time left after eating food with {@code nutrition}: a new heal replaces a smaller one. */
    public static int nourish(int remaining, int nutrition) {
        return Math.min(MAX_NOURISH, Math.max(remaining, Math.max(0, nutrition)));
    }
}
