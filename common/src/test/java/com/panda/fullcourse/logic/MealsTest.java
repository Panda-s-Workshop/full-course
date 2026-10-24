package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Meal slots: fill, refresh, replace the least time, strongest wins, expiry. */
class MealsTest {
    private static Meals.Meal<String> meal(String dish, int level, long end, Family... families) {
        return new Meals.Meal<>(dish, Tier.HEARTY, List.of(families), level, 0, end);
    }

    @Test
    void fillsFreeSlotsInOrder() {
        Meals<String> m = Meals.<String>empty().eat(meal("stew", 2, 100, Family.VIGOR), 2).eat(meal("pie", 3, 200, Family.BRAWN), 2);
        assertEquals(List.of("stew", "pie"), m.active().stream().map(Meals.Meal::dish).toList());
    }

    @Test
    void sameDishRefreshesInPlace() {
        Meals<String> m = Meals.<String>empty().eat(meal("stew", 2, 100, Family.VIGOR), 3).eat(meal("pie", 3, 200, Family.BRAWN), 3)
                .eat(meal("stew", 2, 900, Family.VIGOR), 3);
        assertEquals(2, m.active().size());
        assertEquals("stew", m.active().getFirst().dish());
        assertEquals(900, m.active().getFirst().end());
    }

    @Test
    void fullSlotsReplaceTheMealWithLeastTime() {
        Meals<String> m = Meals.<String>empty().eat(meal("a", 1, 500, Family.SWIFT), 2).eat(meal("b", 1, 300, Family.TIDE), 2)
                .eat(meal("c", 1, 800, Family.EMBER), 2);
        assertEquals(List.of("a", "c"), m.active().stream().map(Meals.Meal::dish).toList());
    }

    @Test
    void strongestLevelWinsPerFamily() {
        Meals<String> m = Meals.<String>empty().eat(meal("a", 1, 500, Family.VIGOR, Family.SWIFT), 3).eat(meal("b", 3, 500, Family.VIGOR), 3);
        assertEquals(Map.of(Family.VIGOR, 3, Family.SWIFT, 1), m.strongest());
    }

    @Test
    void expiryDropsRunOutMealsAndKeepsTheInstanceOtherwise() {
        Meals<String> m = Meals.<String>empty().eat(meal("a", 1, 100, Family.VIGOR), 3).eat(meal("b", 1, 300, Family.TIDE), 3);
        assertSame(m, m.expire(99));
        assertEquals(List.of("b"), m.expire(100).active().stream().map(Meals.Meal::dish).toList());
        assertTrue(m.expire(300).isEmpty());
    }

    @Test
    void fewerSlotsThanMealsShrinksOnNextMeal() {
        Meals<String> m = Meals.<String>empty().eat(meal("a", 1, 100, Family.VIGOR), 3).eat(meal("b", 1, 300, Family.TIDE), 3)
                .eat(meal("c", 1, 400, Family.SWIFT), 3);
        Meals<String> after = m.eat(meal("d", 1, 900, Family.EMBER), 2);
        assertEquals(2, after.active().size());
        assertTrue(after.active().stream().anyMatch(x -> x.dish().equals("d")));
    }

    @Test
    void fractionDrains() {
        Meals.Meal<String> m = new Meals.Meal<>("a", Tier.SIMPLE, List.of(Family.VIGOR), 1, 100, 300);
        assertEquals(1F, m.fraction(100));
        assertEquals(0.5F, m.fraction(200));
        assertEquals(0F, m.fraction(400));
    }
}
