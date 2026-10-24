package com.panda.fullcourse.kitchen;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Heat sources that keep their fire in their block entity instead of a lit block state, such as another mod's oven.
 * Compat code registers a check per block entity class; a heat source with neither a lit state nor a check always
 * heats (magma, lava).
 */
public final class KitchenHeat {
    private static final Map<Class<?>, Predicate<BlockEntity>> CHECKS = new ConcurrentHashMap<>();

    private KitchenHeat() {}

    /** Block entities of {@code type} heat a pot only while {@code burning} says so. */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void register(Class<T> type, Predicate<T> burning) {
        CHECKS.put(type, be -> burning.test((T) be));
    }

    /** Whether the block entity burns, by its registered check; empty when none knows it. */
    public static Optional<Boolean> burning(BlockEntity be) {
        if (be == null) return Optional.empty();
        for (var e : CHECKS.entrySet()) {
            if (e.getKey().isInstance(be)) return Optional.of(e.getValue().test(be));
        }
        return Optional.empty();
    }
}
