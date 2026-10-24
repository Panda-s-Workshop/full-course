package com.panda.fullcourse.logic;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Shapeless matching for the kitchen's stations: every ingredient takes one item from a slot
 * that accepts it, a slot can feed several ingredients up to its count (one stack of sugar covers "sugar, sugar"), and
 * stray items stop the recipe instead of being ignored. Slots holding the same item are one pool (a hopper splits a
 * stack across two slots), and spare slots (empty bowls, bottles and buckets) may stay unused.
 */
public final class IngredientMatch {
    private IngredientMatch() {}

    /** Every slot its own item, none spare. */
    public static <I, S> int[] match(List<I> ingredients, List<S> slots, BiPredicate<I, S> accepts, ToIntFunction<S> count) {
        return match(ingredients, slots, accepts, count, (a, b) -> false, s -> false);
    }

    /**
     * For each ingredient, the index of the slot it takes its item from; {@code null} when an ingredient can't be met or
     * a filled, non-spare pool of slots would be left over. Slots with a count of 0 are empty; {@code same} says two
     * slots hold the same item.
     */
    public static <I, S> int[] match(List<I> ingredients, List<S> slots, BiPredicate<I, S> accepts, ToIntFunction<S> count,
            BiPredicate<S, S> same, Predicate<S> spare) {
        int n = slots.size();
        int[] left = new int[n];
        int[] pool = new int[n];
        boolean[] required = new boolean[n];
        int unused = 0;
        for (int j = 0; j < n; j++) {
            left[j] = Math.max(0, count.applyAsInt(slots.get(j)));
            pool[j] = j;
            if (left[j] == 0) continue;
            for (int k = 0; k < j; k++) {
                if (left[k] > 0 && pool[k] == k && same.test(slots.get(k), slots.get(j))) {
                    pool[j] = k;
                    break;
                }
            }
            if (!spare.test(slots.get(j)) && !required[pool[j]]) {
                required[pool[j]] = true;
                unused++;
            }
        }
        if (unused > ingredients.size()) return null;
        int[] assigned = new int[ingredients.size()];
        int[] uses = new int[n];
        return place(0, ingredients, slots, accepts, left, pool, required, uses, assigned, unused) ? assigned : null;
    }

    /**
     * What is still to add for a recipe: the indices of its ingredients that the slots can't meet, as few as possible, with
     * everything in the slots used as {@link #match} wants. Empty when the recipe matches; {@code null} when the slots hold
     * something the recipe doesn't use, so no amount of adding makes it.
     */
    public static <I, S> int[] missing(List<I> ingredients, List<S> slots, BiPredicate<I, S> accepts, ToIntFunction<S> count,
            BiPredicate<S, S> same, Predicate<S> spare) {
        int n = ingredients.size();
        for (S slot : slots) {
            if (count.applyAsInt(slot) <= 0 || spare.test(slot)) continue;
            if (ingredients.stream().noneMatch(i -> accepts.test(i, slot))) return null;
        }
        // subsets of the ingredients from the largest down: the first that matches leaves the fewest to add (n is at most 6)
        for (int size = n; size >= 0; size--) {
            for (int mask = 0; mask < 1 << n; mask++) {
                if (Integer.bitCount(mask) != size) continue;
                List<I> kept = new java.util.ArrayList<>(size);
                for (int i = 0; i < n; i++) if ((mask & 1 << i) != 0) kept.add(ingredients.get(i));
                if (match(kept, slots, accepts, count, same, spare) == null) continue;
                int[] out = new int[n - size];
                for (int i = 0, k = 0; i < n; i++) if ((mask & 1 << i) == 0) out[k++] = i;
                return out;
            }
        }
        return null;
    }

    private static <I, S> boolean place(int i, List<I> ingredients, List<S> slots, BiPredicate<I, S> accepts, int[] left, int[] pool,
            boolean[] required, int[] uses, int[] assigned, int unused) {
        if (i == ingredients.size()) return unused == 0;
        // with fewer ingredients left than unused pools, some pool stays unused: give up early
        if (ingredients.size() - i < unused) return false;
        for (int j = 0; j < slots.size(); j++) {
            if (left[j] == 0 || !accepts.test(ingredients.get(i), slots.get(j))) continue;
            int p = pool[j];
            left[j]--;
            uses[p]++;
            assigned[i] = j;
            int now = unused - (required[p] && uses[p] == 1 ? 1 : 0);
            if (place(i + 1, ingredients, slots, accepts, left, pool, required, uses, assigned, now)) return true;
            left[j]++;
            uses[p]--;
        }
        return false;
    }
}
