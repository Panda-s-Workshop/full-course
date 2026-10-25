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
    void trimDropsTheMealsWithLeastTimePastTheSlots() {
        Meals<String> m = new Meals<>(List.of(meal("a", 1, 500, Family.SWIFT), meal("b", 1, 300, Family.TIDE), meal("c", 1, 800, Family.EMBER)));
        assertEquals(List.of("a", "c"), m.trim(2).active().stream().map(Meals.Meal::dish).toList());
        assertSame(m, m.trim(3));
        assertEquals(List.of("c"), m.trim(0).active().stream().map(Meals.Meal::dish).toList());
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

    @Test
    void targetSaysWhereADishWouldGo() {
        Meals<String> m = Meals.<String>empty().eat(meal("a", 1, 500, Family.SWIFT), 3).eat(meal("b", 1, 300, Family.TIDE), 3);
        assertEquals(new Meals.Target(1, Meals.Fill.REFRESH), m.target("b", 3));
        assertEquals(new Meals.Target(2, Meals.Fill.FILL), m.target("c", 3));
        assertEquals(new Meals.Target(1, Meals.Fill.REPLACE), m.target("c", 2));
        assertEquals(new Meals.Target(0, Meals.Fill.FILL), Meals.<String>empty().target("a", 2));
    }

    @Test
    void targetAgreesWithEat() {
        var rng = new java.util.Random(7);
        for (int run = 0; run < 500; run++) {
            int slots = 1 + rng.nextInt(5);
            Meals<String> m = Meals.empty();
            for (int k = rng.nextInt(7); k > 0; k--) m = m.eat(meal("d" + rng.nextInt(6), 1, 100 + rng.nextInt(900), Family.VIGOR), slots);
            String dish = "d" + rng.nextInt(7);
            var eaten = meal(dish, 1, 2000 + run, Family.VIGOR);
            Meals.Target t = m.target(dish, slots);
            Meals<String> after = m.eat(eaten, slots);
            assertSame(eaten, after.active().get(t.slot()), "run " + run + ": " + m.active() + " + " + dish + " at " + slots + " slots");
        }
    }
}
