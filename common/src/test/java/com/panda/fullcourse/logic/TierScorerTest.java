package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The scorer on a fake recipe graph shaped like vanilla's and the mod's roster. */
class TierScorerTest {
    private static final Set<String> CONTAINERS = Set.of("bowl", "glass_bottle", "water_bottle", "stick", "bucket");
    private static final Set<String> RAW = Set.of("beef", "rabbit", "potato", "carrot", "wheat", "sugar_cane", "egg", "milk", "pumpkin",
            "brown_mushroom", "red_mushroom", "beetroot", "cocoa", "kelp", "apple", "onion", "bone", "mutton", "cod", "chicken");
    private static final Set<String> NOT_CULINARY = Set.of("gold_nugget", "hay_block", "gold_ingot", "dried_kelp_block");

    private final Map<String, List<TierScorer.Recipe<String>>> recipes = new HashMap<>();
    private final Set<String> placed = new java.util.HashSet<>();
    private final Map<String, List<TierScorer.Recipe<String>>> preps = new HashMap<>();

    private void r(String out, String station, String... ins) {
        r(out, station, 1, ins);
    }

    private void r(String out, String station, int count, String... ins) {
        List<Set<String>> ingredients = new ArrayList<>();
        for (String in : ins) ingredients.add(Set.of(in.split("\\|")));
        int weight = station.equals("crock") ? 2 : 1;
        recipes.computeIfAbsent(out, k -> new ArrayList<>()).add(new TierScorer.Recipe<>(station, weight, ingredients, count));
    }

    private TierScorer<String> scorer() {
        return new TierScorer<>(new TierScorer.Graph<>() {
            @Override public List<TierScorer.Recipe<String>> recipesFor(String item) { return recipes.getOrDefault(item, List.of()); }
            @Override public boolean culinary(String item) { return !NOT_CULINARY.contains(item); }
            @Override public boolean container(String item) { return CONTAINERS.contains(item); }
            @Override public boolean raw(String item) { return RAW.contains(item); }
            @Override public boolean placed(String item) { return placed.contains(item); }
            @Override public List<TierScorer.Recipe<String>> prep(String item) { return preps.getOrDefault(item, List.of()); }
        });
    }

    private void vanilla() {
        r("steak", "fire", "beef");
        r("baked_potato", "fire", "potato");
        r("cooked_rabbit", "fire", "rabbit");
        r("cooked_chicken", "fire", "chicken");
        r("bread", "crafting", "wheat", "wheat", "wheat");
        r("sugar", "crafting", "sugar_cane");
        r("cookie", "crafting", 8, "wheat", "cocoa", "wheat");
        r("mushroom_stew", "crafting", "brown_mushroom", "red_mushroom", "bowl");
        r("beetroot_soup", "crafting", "beetroot", "beetroot", "bowl");
        r("rabbit_stew", "crafting", "cooked_rabbit", "carrot", "baked_potato", "brown_mushroom|red_mushroom", "bowl");
        r("pumpkin_pie", "crafting", "pumpkin", "sugar", "egg");
        r("cake", "crafting", "milk", "milk", "milk", "sugar", "egg", "sugar", "wheat", "wheat", "wheat");
        r("golden_carrot", "crafting", "carrot", "gold_nugget");
        r("gold_nugget", "crafting", 9, "gold_ingot");
        r("wheat", "crafting", 9, "hay_block");
        placed.add("cake");
    }

    private void roster() {
        r("flour", "board", "wheat");
        r("dough", "crafting", "flour", "water_bottle");
        r("butter", "crock", "milk");
        r("pastry", "crafting", "dough", "butter");
        r("broth", "fullcourse:cooking", "bone", "water_bottle");
        r("minced_meat", "board", "beef|mutton");
        r("chopped_vegetables", "board", "carrot|potato|beetroot");
        r("chopped_onion", "board", "onion");
        r("vegetable_stew", "fullcourse:cooking", "chopped_vegetables", "chopped_onion", "baked_potato", "broth", "bowl");
        r("shepherds_pie", "oven", "minced_meat", "chopped_onion", "baked_potato", "butter");
        r("roast_banquet", "oven", "cooked_chicken", "dough", "chopped_vegetables", "broth");
        placed.add("roast_banquet");
    }

    /** A raw item made only by a cut: the cut counts toward the dish, and the raw item stays its leaf. */
    @Test
    void aCutCountsTowardTheDishAndTheCutStaysTheLeaf() {
        vanilla();
        r("patty", "fire", "minced_beef");
        TierScorer.Score<String> before = scorer().score("patty");
        preps.put("minced_beef", List.of(new TierScorer.Recipe<>("cutting", 1, List.of(Set.of("beef")), 2)));
        Set<String> raw = new java.util.HashSet<>(RAW);
        raw.add("minced_beef");
        TierScorer<String> withCut = new TierScorer<>(new TierScorer.Graph<>() {
            @Override public List<TierScorer.Recipe<String>> recipesFor(String item) { return recipes.getOrDefault(item, List.of()); }
            @Override public boolean culinary(String item) { return !NOT_CULINARY.contains(item); }
            @Override public boolean container(String item) { return CONTAINERS.contains(item); }
            @Override public boolean raw(String item) { return raw.contains(item); }
            @Override public boolean placed(String item) { return placed.contains(item); }
            @Override public List<TierScorer.Recipe<String>> prep(String item) { return preps.getOrDefault(item, List.of()); }
        });
        TierScorer.Score<String> patty = withCut.score("patty");
        assertEquals(1, before.depth());
        assertEquals(2, patty.depth(), "cut, then cooked");
        assertEquals(Set.of("fire", "cutting"), patty.stations().keySet());
        assertEquals(Set.of("minced_beef"), patty.leaves(), "the cut stays the leaf, so its own groups count");
        assertEquals(before.score() + 2, patty.score());
        assertEquals(new TierScorer.Score<>(2, 1, Map.of("cutting", 1), Set.of("minced_beef")), withCut.score("minced_beef"));
    }

    private int score(String item) {
        return scorer().score(item).score();
    }

    private Tier tier(String item) {
        return Tier.fromScore(score(item), Tier.DEFAULT_THRESHOLDS);
    }

    @Test
    void rawFoodIsTierZero() {
        vanilla();
        assertEquals(0, score("beef"));
        assertEquals(Tier.RAW, tier("apple"));
    }

    @Test
    void vanillaTableFromTheMdd() {
        vanilla();
        for (String simple : List.of("steak", "bread", "cookie", "mushroom_stew", "beetroot_soup")) {
            assertEquals(2, score(simple), simple);
            assertEquals(Tier.SIMPLE, tier(simple), simple);
        }
        assertEquals(4, score("pumpkin_pie"));
        assertEquals(Tier.PREPARED, tier("pumpkin_pie"));
        assertEquals(5, score("rabbit_stew"));
        assertEquals(Tier.HEARTY, tier("rabbit_stew"));
        assertEquals(7, score("cake"), "4 + placed bonus 3");
        assertEquals(Tier.HEARTY, tier("cake"));
    }

    @Test
    void scorePartsAreReported() {
        vanilla();
        TierScorer.Score<String> s = scorer().score("rabbit_stew");
        assertEquals(2, s.depth());
        assertEquals(2, s.stationWeight(), "crafting + fire");
        assertEquals(4, s.leaves().size(), "rabbit, carrot, potato and one mushroom: " + s.leaves());
        assertTrue(s.leaves().containsAll(Set.of("rabbit", "carrot", "potato")));
    }

    @Test
    void nonCulinaryIngredientsAreLeaves() {
        vanilla();
        // the gold nugget is a leaf, not a chain through ingots
        assertEquals(2, score("golden_carrot"));
    }

    @Test
    void unpackingRecipesDontMakeRawItemsDeep() {
        vanilla();
        // wheat is raw anyway; a non-raw item made only by unpacking stays a leaf
        r("dried_kelp", "crafting", 9, "dried_kelp_block");
        assertEquals(0, score("dried_kelp"));
        r("dried_kelp", "fire", "kelp");
        assertEquals(2, score("dried_kelp"));
    }

    @Test
    void cyclesAreCut() {
        r("a", "crafting", "b");
        r("b", "crafting", "a");
        r("b", "fire", "beef");
        // a <- b <- (a | beef): the cycle through a is skipped, b is made from beef
        assertEquals(2, score("b"));
        assertEquals(4, score("a"));
    }

    @Test
    void cheapestRecipeAndCheapestAlternativeWin() {
        r("x", "crafting", "beef");
        r("x", "oven", "steak", "egg", "milk");
        r("steak", "fire", "beef");
        assertEquals(2, score("x"));
    }

    @Test
    void crockCountsDouble() {
        vanilla();
        roster();
        TierScorer.Score<String> butter = scorer().score("butter");
        assertEquals(3, butter.score(), "depth 1 + crock 2");
    }

    @Test
    void rosterTiersFromTheMdd() {
        vanilla();
        roster();
        assertEquals(6, score("vegetable_stew"));
        assertEquals(Tier.HEARTY, tier("vegetable_stew"));
        assertEquals(8, score("shepherds_pie"));
        assertEquals(Tier.GOURMET, tier("shepherds_pie"));
        assertEquals(12, score("roast_banquet"), "9 + placed bonus 3");
        assertEquals(Tier.FEAST, tier("roast_banquet"));
    }

    @Test
    void familiesCountGroupsMostFirstThenFamilyOrder() {
        Map<String, Set<Family>> groups = Map.of(
                "beef", Set.of(Family.BRAWN), "onion", Set.of(Family.VIGOR), "carrot", Set.of(Family.VIGOR),
                "milk", Set.of(Family.FORTITUDE), "stew", Set.of(Family.MENDING));
        java.util.function.Function<String, Set<Family>> of = s -> groups.getOrDefault(s, Set.of());
        assertEquals(List.of(Family.VIGOR, Family.BRAWN), TierScorer.families("pie", Set.of("beef", "onion", "carrot", "milk"), of, 2));
        assertEquals(List.of(Family.VIGOR, Family.BRAWN, Family.FORTITUDE), TierScorer.families("pie", Set.of("beef", "onion", "carrot", "milk"), of, 3));
        assertEquals(List.of(Family.MENDING), TierScorer.families("stew", Set.of(), of, 2), "the food's own group counts");
        assertEquals(List.of(), TierScorer.families("x", Set.of("y"), of, 2));
    }

    @Test
    void thresholdsAreConfigurable() {
        assertEquals(Tier.RAW, Tier.fromScore(0, Tier.DEFAULT_THRESHOLDS));
        assertEquals(Tier.SIMPLE, Tier.fromScore(2, Tier.DEFAULT_THRESHOLDS));
        assertEquals(Tier.PREPARED, Tier.fromScore(3, Tier.DEFAULT_THRESHOLDS));
        assertEquals(Tier.HEARTY, Tier.fromScore(7, Tier.DEFAULT_THRESHOLDS));
        assertEquals(Tier.GOURMET, Tier.fromScore(11, Tier.DEFAULT_THRESHOLDS));
        assertEquals(Tier.FEAST, Tier.fromScore(40, Tier.DEFAULT_THRESHOLDS));
        assertEquals(Tier.SIMPLE, Tier.fromScore(3, new int[] {1, 4, 6, 9, 14}));
    }
}
