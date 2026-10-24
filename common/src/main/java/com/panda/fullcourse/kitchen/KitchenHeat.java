package com.panda.fullcourse.kitchen;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * When a block in {@code #fullcourse:heat_sources} is burning. In order: a check registered for its block entity's class
 * (the closest class wins, for another mod's oven that keeps its fire there); a {@code lit} or {@code burning} block
 * state; otherwise always (fire, magma, lava). Compat code also adds a line to the pot's "needs heat" tooltip.
 */
public final class KitchenHeat {
    private static final Map<Class<?>, Predicate<BlockEntity>> CHECKS = new ConcurrentHashMap<>();
    private static final List<Component> NOTES = new CopyOnWriteArrayList<>();

    private KitchenHeat() {}

    /** Block entities of {@code type} (and its subclasses, unless they have their own) heat a pot only while {@code burning}. */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void register(Class<T> type, Predicate<T> burning) {
        CHECKS.put(type, be -> burning.test((T) be));
    }

    /** A line for the pot's "needs heat" tooltip, naming a heat source another mod adds. */
    public static void note(Component line) {
        NOTES.add(line);
    }

    public static List<Component> notes() {
        return List.copyOf(NOTES);
    }

    /** Whether a heat source in this state, with this block entity, is burning. */
    public static boolean burning(BlockState state, BlockEntity be) {
        if (be != null) {
            for (Class<?> c = be.getClass(); c != null && c != BlockEntity.class; c = c.getSuperclass()) {
                Predicate<BlockEntity> check = CHECKS.get(c);
                if (check != null) return check.test(be);
            }
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty flag && (flag.getName().equals("lit") || flag.getName().equals("burning"))) {
                return state.getValue(flag);
            }
        }
        return true;
    }
}
