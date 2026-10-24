package com.panda.fullcourse.logic;

import java.util.Locale;
import java.util.Optional;

/**
 * A benefit family. Which families a food gives comes from its ingredients' groups, the item tags
 * {@code fullcourse:groups/<name>}. The order here breaks ties between groups with equal counts.
 */
public enum Family {
    BRAWN,
    TIDE,
    VIGOR,
    SWIFT,
    DILIGENCE,
    FORTUNE,
    FORTITUDE,
    NIGHT_EYE,
    EMBER,
    LIGHTFOOT,
    MENDING;

    /** The lowercase name used in tags, data files and lang keys: {@code night_eye}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Family> byKey(String key) {
        for (Family f : values()) {
            if (f.key().equals(key)) return Optional.of(f);
        }
        return Optional.empty();
    }
}
