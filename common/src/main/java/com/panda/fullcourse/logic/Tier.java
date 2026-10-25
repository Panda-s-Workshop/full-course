package com.panda.fullcourse.logic;

/**
 * A dish's tier: how many benefits it gives, at what level, for how long. The score that picks the tier comes
 * from {@link TierScorer}. Feast's level includes the feast bonus (+1).
 */
public enum Tier {
    RAW(0, 0, 0),
    SIMPLE(1, 1, 2),
    PREPARED(2, 1, 5),
    HEARTY(2, 2, 10),
    GOURMET(3, 3, 20),
    FEAST(3, 4, 30);

    /** Lowest score of SIMPLE, PREPARED, HEARTY, GOURMET and FEAST (config `tierThresholds`). */
    public static final int[] DEFAULT_THRESHOLDS = {1, 3, 5, 8, 12};

    private final int benefits;
    private final int level;
    private final int minutes;

    Tier(int benefits, int level, int minutes) {
        this.benefits = benefits;
        this.level = level;
        this.minutes = minutes;
    }

    /** How many benefit families a dish of this tier gives. */
    public int benefits() {
        return benefits;
    }

    /** The level of each benefit, 1..4. */
    public int level() {
        return level;
    }

    public int minutes() {
        return minutes;
    }

    /** The benefit duration in ticks, scaled by the config's duration multiplier. */
    public int durationTicks(double multiplier) {
        return (int) Math.round(minutes * 60 * 20 * multiplier);
    }

    /** 0 for RAW .. 5 for FEAST: the number of stars on the tooltip. */
    public int stars() {
        return ordinal();
    }

    public static Tier ofStars(int stars) {
        return values()[Math.clamp(stars, 0, 5)];
    }

    /** The tier a score reaches; {@code thresholds} are the lowest scores of tiers 1..5, ascending. */
    public static Tier fromScore(int score, int[] thresholds) {
        Tier tier = RAW;
        for (int i = 0; i < thresholds.length && i < 5; i++) {
            if (score >= thresholds[i]) tier = values()[i + 1];
        }
        return tier;
    }

    /** The tier's colour, ARGB: grey, copper, iron, gold, emerald, amethyst. */
    public int color() {
        return switch (this) {
            case RAW -> 0xFF8A8A8A;
            case SIMPLE -> 0xFFC8794A;
            case PREPARED -> 0xFFD6D6D6;
            case HEARTY -> 0xFFF2C94C;
            case GOURMET -> 0xFF3DD47A;
            case FEAST -> 0xFFB57BEA;
        };
    }

    /** The dark grey of a meal frame's border, which {@link #trackColor} dims toward. */
    public static final int FRAME_GREY = 0xFF262626;

    /**
     * The tier's colour dimmed 60% of the way toward {@link #FRAME_GREY}: the track under a meal's draining ring, so the
     * part of the time already gone still shows the tier.
     */
    public int trackColor() {
        int c = color(), out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int ch = (c >> shift) & 0xFF, grey = (FRAME_GREY >> shift) & 0xFF;
            out |= (grey + Math.round((ch - grey) * 0.4F)) << shift;
        }
        return out;
    }

    /** Benefit-minutes: families × level × minutes, the measure the balance curve is pinned on. */
    public int benefitMinutes() {
        return benefits * level * minutes;
    }
}
