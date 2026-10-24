package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cooking Pot matching: shapeless, one stack can feed repeated ingredients, no stray items. */
class IngredientMatchTest {
    /** A slot: an item name and a count. An ingredient: the names it accepts. */
    private record Slot(String item, int count) {}

    private static int[] match(List<Set<String>> ingredients, Slot... slots) {
        return IngredientMatch.match(ingredients, List.of(slots), (ing, slot) -> ing.contains(slot.item()), Slot::count);
    }

    private static final Slot EMPTY = new Slot("air", 0);

    @Test
    void matchesInAnyOrder() {
        int[] m = match(List.of(Set.of("meat"), Set.of("onion")), new Slot("onion", 3), EMPTY, new Slot("meat", 1));
        assertArrayEquals(new int[] {2, 0}, m);
    }

    @Test
    void oneStackFeedsRepeatedIngredients() {
        assertArrayEquals(new int[] {0, 0}, match(List.of(Set.of("sugar"), Set.of("sugar")), new Slot("sugar", 64)));
    }

    @Test
    void repeatedIngredientsNeedEnoughItems() {
        assertNull(match(List.of(Set.of("sugar"), Set.of("sugar")), new Slot("sugar", 1)));
        assertArrayEquals(new int[] {0, 1}, match(List.of(Set.of("sugar"), Set.of("sugar")), new Slot("sugar", 1), new Slot("sugar", 1)));
    }

    @Test
    void missingIngredientFails() {
        assertNull(match(List.of(Set.of("meat"), Set.of("onion")), new Slot("meat", 5)));
    }

    @Test
    void strayItemFails() {
        assertNull(match(List.of(Set.of("meat")), new Slot("meat", 1), new Slot("dirt", 1)));
    }

    @Test
    void strayItemFailsWhenOneStackCoversEverything() {
        // two sugar ingredients both fit the sugar stack: the dirt is still left over
        assertNull(match(List.of(Set.of("sugar"), Set.of("sugar")), new Slot("sugar", 64), new Slot("dirt", 1)));
    }

    @Test
    void alternativesBacktrack() {
        // the first ingredient accepts both; a greedy pick of "carrot" would starve the second
        int[] m = match(List.of(Set.of("carrot", "potato"), Set.of("carrot")), new Slot("carrot", 1), new Slot("potato", 1));
        assertArrayEquals(new int[] {1, 0}, m);
    }

    @Test
    void emptyPotMatchesNothing() {
        assertNull(match(List.of(Set.of("bone")), EMPTY, EMPTY));
    }

    /** The pot's rules: slots with the same item are one pool, empties (bottles, bowls, buckets) are spare. */
    private static int[] pot(List<Set<String>> ingredients, Slot... slots) {
        return IngredientMatch.match(ingredients, List.of(slots), (ing, slot) -> ing.contains(slot.item()), Slot::count,
                (a, b) -> a.item().equals(b.item()), s -> s.item().equals("bottle"));
    }

    @Test
    void splitStackIsOnePool() {
        // a hopper put two bones in two slots: the one-bone recipe still matches
        assertNotNull(pot(List.of(Set.of("bone")), new Slot("bone", 1), new Slot("bone", 1)));
        assertNull(match(List.of(Set.of("bone")), new Slot("bone", 1), new Slot("bone", 1)));
    }

    @Test
    void emptiesAreSpareButStraysStillFail() {
        assertNotNull(pot(List.of(Set.of("bone")), new Slot("bottle", 1), new Slot("bone", 1)));
        assertNull(pot(List.of(Set.of("bone")), new Slot("bottle", 1), new Slot("bone", 1), new Slot("dirt", 1)));
        assertNull(pot(List.of(Set.of("bone")), new Slot("bottle", 1)));
    }

    @Test
    void spareCanStillBeAnIngredient() {
        assertArrayEquals(new int[] {0, 1}, pot(List.of(Set.of("bottle"), Set.of("sugar")), new Slot("bottle", 1), new Slot("sugar", 1)));
    }

    private static int[] missing(List<Set<String>> ingredients, Slot... slots) {
        return IngredientMatch.missing(ingredients, List.of(slots), (ing, slot) -> ing.contains(slot.item()), Slot::count,
                (a, b) -> a.item().equals(b.item()), slot -> slot.item().equals("bowl"));
    }

    @Test
    void missingIsEmptyWhenTheRecipeMatches() {
        assertArrayEquals(new int[0], missing(List.of(Set.of("meat"), Set.of("onion")), new Slot("onion", 1), new Slot("meat", 1)));
    }

    @Test
    void missingNamesWhatIsStillToAdd() {
        List<Set<String>> stew = List.of(Set.of("meat"), Set.of("onion"), Set.of("broth"));
        assertArrayEquals(new int[] {1, 2}, missing(stew, new Slot("meat", 1)));
        assertArrayEquals(new int[] {0, 1, 2}, missing(stew, EMPTY), "an empty pot misses everything");
    }

    @Test
    void missingCountsRepeatsAndSpares() {
        assertArrayEquals(new int[] {1}, missing(List.of(Set.of("sugar"), Set.of("sugar")), new Slot("sugar", 1)), "one sugar of two");
        assertArrayEquals(new int[] {1}, missing(List.of(Set.of("meat"), Set.of("onion")), new Slot("meat", 1), new Slot("bowl", 1)),
                "an empty bowl in the grid is no stray");
    }

    @Test
    void aStrayItemMeansTheRecipeCantBeMade() {
        assertNull(missing(List.of(Set.of("meat"), Set.of("onion")), new Slot("meat", 1), new Slot("dirt", 1)));
    }
}
