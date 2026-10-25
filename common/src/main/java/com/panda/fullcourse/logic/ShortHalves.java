package com.panda.fullcourse.logic;

/**
 * An int sent through two menu data slots, which travel as shorts: a raw item id, which a big pack takes past what one
 * short holds. Stored as {@code id + 1}, so none (-1) is 0.
 */
public final class ShortHalves {
    private ShortHalves() {}

    /** The low 16 bits. */
    public static int low(int id) {
        return (id + 1) & 0xFFFF;
    }

    /** The high 16 bits. */
    public static int high(int id) {
        return (id + 1) >>> 16;
    }

    /** The id from its halves as they arrive, each sign-extended from a short. */
    public static int join(int low, int high) {
        return ((low & 0xFFFF) | (high & 0xFFFF) << 16) - 1;
    }
}
