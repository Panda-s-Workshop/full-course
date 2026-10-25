package com.panda.fullcourse.logic;

/**
 * Meal slots earned by tasting: the third at 8 different dishes of two stars or more, the fourth at 15 of three stars
 * or more, the fifth at 20 of three stars or more plus a serving from a feast. Each milestone needs the one before.
 * A base install has 23 dishes of three stars or more (21 of the mod's and vanilla's rabbit stew and cake), so the
 * fifth slot asks for nearly all of them.
 */
public final class Milestones {
    public static final int THIRD = 8, FOURTH = 15, FIFTH = 20;

    private Milestones() {}

    /**
     * What a player has tasted, counted.
     *
     * @param prepared distinct dishes of two stars or more
     * @param hearty distinct dishes of three stars or more
     * @param feast whether a feast was among them
     */
    public record Count(int prepared, int hearty, boolean feast) {}

    /**
     * The next slot to earn and how far along it is; {@code slot} is 0 once all three are earned.
     *
     * @param slot 3, 4 or 5 (counting from the two every player starts with)
     * @param stars the dish tier this milestone counts (2 or 3)
     * @param have dishes of that tier tasted
     * @param need dishes of that tier it asks for
     * @param needsFeast whether a feast serving is still missing
     */
    public record Next(int slot, int stars, int have, int need, boolean needsFeast) {
        public static final Next NONE = new Next(0, 0, 0, 0, false);
    }

    /** How many of the three milestones are reached (0..3), in order. */
    public static int reached(Count c) {
        if (c.prepared() < THIRD) return 0;
        if (c.hearty() < FOURTH) return 1;
        if (c.hearty() < FIFTH || !c.feast()) return 2;
        return 3;
    }

    public static Next next(Count c) {
        return next(c, 0);
    }

    /**
     * Whether tasting a new meal dish of {@code tier} would move {@code next} along: one of its tier or higher while
     * dishes are still short, or a feast while the fifth slot waits only on one.
     */
    public static boolean counts(Next next, Tier tier) {
        if (next == Next.NONE || tier.stars() < next.stars()) return false;
        return next.have() < next.need() || next.needsFeast() && tier == Tier.FEAST;
    }

    /**
     * The next milestone past those reached and those already {@code earned} (slots given by a command, or earned before
     * a datapack rated dishes lower): the one whose slot the player doesn't have yet.
     */
    public static Next next(Count c, int earned) {
        return switch (Math.max(reached(c), earned)) {
            case 0 -> new Next(3, 2, c.prepared(), THIRD, false);
            case 1 -> new Next(4, 3, c.hearty(), FOURTH, false);
            case 2 -> new Next(5, 3, c.hearty(), FIFTH, !c.feast());
            default -> Next.NONE;
        };
    }
}
