package com.panda.fullcourse.logic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The meals a player holds: immutable, one entry per dish. Eating a dish already held refreshes it; a new
 * dish fills a free slot or replaces the meal with the least time left. Same-family benefits don't add up: the
 * strongest level wins.
 *
 * @param <D> the dish id type
 */
public record Meals<D>(List<Meal<D>> active) {
    /**
     * One held meal.
     *
     * @param start game time it was eaten
     * @param end game time it runs out
     */
    public record Meal<D>(D dish, Tier tier, List<Family> families, int level, long start, long end) {
        public long remaining(long now) {
            return Math.max(0, end - now);
        }

        /** 1 when just eaten, 0 when it runs out. */
        public float fraction(long now) {
            long total = Math.max(1, end - start);
            return Math.clamp((float) remaining(now) / total, 0F, 1F);
        }
    }

    public Meals {
        active = List.copyOf(active);
    }

    public static <D> Meals<D> empty() {
        return new Meals<>(List.of());
    }

    /** How eating a dish would place it: refresh the same dish, fill a free slot, or replace the meal with least time. */
    public enum Fill { REFRESH, FILL, REPLACE }

    /** The slot (an index into {@link #active()}, or its size for a new slot) a dish would take, and how. */
    public record Target(int slot, Fill fill) {}

    /** Where eating {@code dish} with {@code slots} slots would put it, as {@link #eat} does. */
    public Target target(D dish, int slots) {
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).dish().equals(dish)) return new Target(i, Fill.REFRESH);
        }
        if (active.size() < slots) return new Target(active.size(), Fill.FILL);
        return new Target(leastTime(active.subList(0, Math.min(active.size(), Math.max(1, slots)))), Fill.REPLACE);
    }

    /** Eats a meal with {@code slots} slots available. */
    public Meals<D> eat(Meal<D> meal, int slots) {
        List<Meal<D>> next = new ArrayList<>(active);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).dish().equals(meal.dish())) {
                next.set(i, meal);
                return new Meals<>(next);
            }
        }
        while (next.size() > Math.max(1, slots)) next.remove(leastTime(next)); // slots lowered by config
        if (next.size() < slots) {
            next.add(meal);
        } else {
            next.set(leastTime(next), meal);
        }
        return new Meals<>(next);
    }

    private static <D> int leastTime(List<Meal<D>> meals) {
        int at = 0;
        for (int i = 1; i < meals.size(); i++) {
            if (meals.get(i).end() < meals.get(at).end()) at = i;
        }
        return at;
    }

    /** Drops the meals with least time left until at most {@code slots} remain (the config lowered); else the same instance. */
    public Meals<D> trim(int slots) {
        if (active.size() <= Math.max(1, slots)) return this;
        List<Meal<D>> next = new ArrayList<>(active);
        while (next.size() > Math.max(1, slots)) next.remove(leastTime(next));
        return new Meals<>(next);
    }

    /** Drops the meals that have run out; the same instance when none has. */
    public Meals<D> expire(long now) {
        boolean any = false;
        for (Meal<D> m : active) any |= m.end() <= now;
        if (!any) return this;
        List<Meal<D>> next = new ArrayList<>();
        for (Meal<D> m : active) {
            if (m.end() > now) next.add(m);
        }
        return new Meals<>(next);
    }

    /** The level of each family across all meals: the strongest wins. */
    public Map<Family, Integer> strongest() {
        Map<Family, Integer> levels = new EnumMap<>(Family.class);
        for (Meal<D> m : active) {
            for (Family f : m.families()) levels.merge(f, m.level(), Math::max);
        }
        return levels;
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }
}
