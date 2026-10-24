package com.panda.fullcourse.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores how much work a food takes from its recipe chain:
 * {@code score = depth + station weight + distinct leaves / 3}, over the cheapest recipe, plus 3 for placed meals.
 * Pure: the caller describes the recipe graph through {@link Graph}, so the same code scores a fake graph in tests and
 * the server's recipe manager in game.
 *
 * @param <T> the item type
 */
public final class TierScorer<T> {
    /** Score added to placed meals (feasts, cake): assembling one is a step of its own. */
    public static final int PLACED_BONUS = 3;
    /** A single-ingredient recipe that yields this many or more from a non-culinary item is unpacking (hay → wheat). */
    public static final int UNPACK_COUNT = 4;

    /**
     * One recipe that makes an item.
     *
     * @param station the station id ("crafting", "fire", "fullcourse:cooking", or another mod's recipe type)
     * @param weight how much the station counts (1, the crock 2)
     * @param ingredients one set of accepted items per ingredient slot
     * @param count how many items the recipe makes
     */
    public record Recipe<T>(String station, int weight, List<Set<T>> ingredients, int count) {}

    /** What the scorer needs to know about the world. */
    public interface Graph<T> {
        List<Recipe<T>> recipesFor(T item);

        /** Food, or an ingredient on its way to food: the scorer follows these down to their leaves. */
        boolean culinary(T item);

        /** Bowls, bottles, buckets, sticks: ignored. */
        boolean container(T item);

        /** Obtained raw (crops, drops, fruit): always a leaf, whatever recipes also make it. */
        boolean raw(T item);

        /** Placed meals get {@link #PLACED_BONUS}. */
        boolean placed(T item);
    }

    /**
     * A food's score and its parts, for the tooltip.
     *
     * @param stations distinct stations used, with their weights
     * @param leaves the distinct raw items the cheapest chain starts from
     */
    public record Score<T>(int score, int depth, Map<String, Integer> stations, Set<T> leaves) {
        public int stationWeight() {
            int w = 0;
            for (int v : stations.values()) w += v;
            return w;
        }

        static <T> Score<T> leaf(T item) {
            return new Score<>(0, 0, Map.of(), Set.of(item));
        }
    }

    private final Graph<T> graph;
    private final Map<T, Score<T>> memo = new HashMap<>();
    private final Set<T> onStack = new HashSet<>();

    public TierScorer(Graph<T> graph) {
        this.graph = graph;
    }

    /** The item's score with the placed-meal bonus. */
    public Score<T> score(T item) {
        Score<T> base = best(item);
        if (base == null) base = Score.leaf(item);
        if (!graph.placed(item)) return base;
        return new Score<>(base.score() + PLACED_BONUS, base.depth(), base.stations(), base.leaves());
    }

    /** The cheapest chain to the item, or null when every recipe for it runs into a cycle. */
    private Score<T> best(T item) {
        Score<T> known = memo.get(item);
        if (known != null) return known;
        if (onStack.contains(item)) return null;
        List<Recipe<T>> recipes = graph.raw(item) ? List.of() : graph.recipesFor(item);
        if (recipes.isEmpty()) {
            Score<T> leaf = Score.leaf(item);
            memo.put(item, leaf);
            return leaf;
        }
        onStack.add(item);
        Score<T> bestScore = null;
        boolean cycled = false;
        for (Recipe<T> recipe : recipes) {
            if (isUnpacking(recipe)) continue;
            Score<T> s = viaRecipe(recipe);
            if (s == null) {
                cycled = true;
                continue;
            }
            if (bestScore == null || s.score() < bestScore.score() || (s.score() == bestScore.score() && s.depth() < bestScore.depth())) {
                bestScore = s;
            }
        }
        onStack.remove(item);
        if (bestScore == null && !cycled) bestScore = Score.leaf(item); // only unpacking recipes: a raw item
        if (bestScore != null) memo.put(item, bestScore);
        return bestScore;
    }

    private boolean isUnpacking(Recipe<T> recipe) {
        if (recipe.ingredients().size() != 1 || recipe.count() < UNPACK_COUNT) return false;
        for (T alt : recipe.ingredients().getFirst()) {
            if (graph.culinary(alt)) return false;
        }
        return true;
    }

    private Score<T> viaRecipe(Recipe<T> recipe) {
        int depth = 0;
        Map<String, Integer> stations = new LinkedHashMap<>();
        stations.put(recipe.station(), recipe.weight());
        Set<T> leaves = new LinkedHashSet<>();
        for (Set<T> ingredient : recipe.ingredients()) {
            Score<T> cheapest = null;
            boolean onlyContainers = true;
            for (T alt : ingredient) {
                if (graph.container(alt)) continue;
                onlyContainers = false;
                Score<T> s = graph.culinary(alt) ? best(alt) : Score.leaf(alt);
                if (s != null && (cheapest == null || s.score() < cheapest.score())) cheapest = s;
            }
            if (onlyContainers) continue;
            if (cheapest == null) return null;
            depth = Math.max(depth, cheapest.depth());
            for (Map.Entry<String, Integer> e : cheapest.stations().entrySet()) stations.merge(e.getKey(), e.getValue(), Math::max);
            leaves.addAll(cheapest.leaves());
        }
        depth += 1;
        int weight = 0;
        for (int w : stations.values()) weight += w;
        int score = depth + weight + leaves.size() / 3;
        return new Score<>(score, depth, Collections.unmodifiableMap(stations), Collections.unmodifiableSet(leaves));
    }

    /**
     * The families a food gives: the groups of its leaves (and of the food itself) counted, most first, ties in
     * {@link Family} order, the first {@code n}.
     */
    public static <T> List<Family> families(T item, Set<T> leaves, java.util.function.Function<T, Set<Family>> groupsOf, int n) {
        int[] counts = new int[Family.values().length];
        Set<T> all = new LinkedHashSet<>(leaves);
        all.add(item);
        for (T t : all) {
            for (Family f : groupsOf.apply(t)) counts[f.ordinal()]++;
        }
        List<Family> present = new ArrayList<>();
        for (Family f : Family.values()) {
            if (counts[f.ordinal()] > 0) present.add(f);
        }
        present.sort((a, b) -> counts[b.ordinal()] != counts[a.ordinal()] ? counts[b.ordinal()] - counts[a.ordinal()] : a.ordinal() - b.ordinal());
        return List.copyOf(present.subList(0, Math.min(n, present.size())));
    }
}
