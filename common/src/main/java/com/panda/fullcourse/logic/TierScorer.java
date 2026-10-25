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
    /** Score added to a dish served from another mod's block: it's assembled, but its servings aren't a feast's. */
    public static final int SERVED_BONUS = 1;
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

        /** Other dishes served from a block get {@link #SERVED_BONUS}. */
        default boolean served(T item) {
            return false;
        }

        /**
         * How a raw item is prepared when that is the only way to get it (a cut): its depth and stations count toward
         * what's made from it, and the item itself stays the leaf.
         */
        default List<Recipe<T>> prep(T item) {
            return List.of();
        }
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

    /**
     * How many items the scorer may explore before it remembers every result, cycle cuts included: a pack whose cycle
     * groups would take longer gets order-dependent scores rather than a slow load.
     */
    static final int VISIT_BUDGET = 200_000;

    private final Graph<T> graph;
    private final Map<T, Score<T>> memo = new HashMap<>();
    private final Set<T> onStack = new HashSet<>();
    /**
     * Per item being explored, the items further up the stack its recipes ran into. A result that cut such a cycle
     * holds only for this visit order, so it isn't remembered: scored from elsewhere, the item may do better.
     */
    private final java.util.Deque<Set<T>> stackHits = new java.util.ArrayDeque<>();
    private int visits;

    public TierScorer(Graph<T> graph) {
        this.graph = graph;
    }

    /** The item's score with the placed-meal or served-dish bonus. */
    public Score<T> score(T item) {
        Score<T> base = best(item);
        if (base == null) base = Score.leaf(item);
        int bonus = graph.placed(item) ? PLACED_BONUS : graph.served(item) ? SERVED_BONUS : 0;
        if (bonus == 0) return base;
        return new Score<>(base.score() + bonus, base.depth(), base.stations(), base.leaves());
    }

    /** The cheapest chain to the item, or null when every recipe for it runs into a cycle. */
    private Score<T> best(T item) {
        Score<T> known = memo.get(item);
        if (known != null) return known;
        if (onStack.contains(item)) {
            if (!stackHits.isEmpty()) stackHits.peek().add(item);
            return null;
        }
        visits++;
        if (graph.raw(item) && !graph.prep(item).isEmpty()) return prepared(item);
        List<Recipe<T>> recipes = graph.raw(item) ? List.of() : graph.recipesFor(item);
        if (recipes.isEmpty()) {
            Score<T> leaf = Score.leaf(item);
            memo.put(item, leaf);
            return leaf;
        }
        enter(item);
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
        boolean settled = leave(item);
        if (bestScore == null && !cycled) bestScore = Score.leaf(item); // only unpacking recipes: a raw item
        if (bestScore != null && settled) memo.put(item, bestScore);
        return bestScore;
    }

    private void enter(T item) {
        onStack.add(item);
        stackHits.push(new HashSet<>());
    }

    /** Leaves the item; true when its result holds whatever the visit order (or the budget is spent). */
    private boolean leave(T item) {
        onStack.remove(item);
        Set<T> hits = stackHits.pop();
        hits.remove(item); // a recipe that needs the item itself is no way to make it, in any order
        if (!stackHits.isEmpty()) stackHits.peek().addAll(hits);
        return hits.isEmpty() || visits > VISIT_BUDGET;
    }

    /** Items explored so far, for the log. */
    public int visits() {
        return visits;
    }

    /** A raw item through its cheapest preparation, still its own leaf; a leaf if every preparation cycles. */
    private Score<T> prepared(T item) {
        enter(item);
        Score<T> best = null;
        for (Recipe<T> recipe : graph.prep(item)) {
            Score<T> s = viaRecipe(recipe);
            if (s != null && (best == null || s.score() < best.score())) best = s;
        }
        boolean settled = leave(item);
        Score<T> out = best == null ? Score.leaf(item)
                : new Score<>(best.depth() + best.stationWeight(), best.depth(), best.stations(), Set.of(item)); // one leaf: no leaf bonus
        if (settled) memo.put(item, out);
        return out;
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

    /**
     * The {@code n} benefits a food rated from its recipes gives: its {@link #families}, then two rules. A soup always
     * mends: Mending joins if there's room, else takes the least-counted place. A food whose groups don't fill its
     * tier's count is made up with Mending, so a two-star salad of vegetables alone still gives two benefits: a good
     * meal helps you heal.
     */
    public static <T> List<Family> benefits(T item, Set<T> leaves, java.util.function.Function<T, Set<Family>> groupsOf, boolean soup, int n) {
        if (n <= 0) return List.of();
        List<Family> out = new ArrayList<>(families(item, leaves, groupsOf, n));
        if (soup && !out.contains(Family.MENDING)) {
            if (out.size() == n) out.removeLast();
            out.add(Family.MENDING);
        }
        if (out.size() < n && !out.contains(Family.MENDING)) out.add(Family.MENDING);
        return List.copyOf(out);
    }
}
