package com.panda.fullcourse.checks;

import static com.panda.fullcourse.checks.RuleChecks.check;
import static com.panda.fullcourse.checks.RuleChecks.dishes;
import static com.panda.fullcourse.checks.RuleChecks.eat;
import static com.panda.fullcourse.checks.RuleChecks.health;
import static com.panda.fullcourse.checks.RuleChecks.hurtAt;
import static com.panda.fullcourse.checks.RuleChecks.id;
import static com.panda.fullcourse.checks.RuleChecks.profile;
import static com.panda.fullcourse.checks.RuleChecks.state;
import static com.panda.fullcourse.checks.RuleChecks.ticks;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.logic.Tier;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.world.item.Items;

/**
 * Each config key changes what it names: the checks run with one key changed through
 * {@link FullCourseConfig#with}, the rest at their defaults.
 */
final class ConfigChecks {
    private static final FullCourseConfig.Snapshot D = FullCourseConfig.Snapshot.of(FullCourseConfig.DEFAULTS);

    private ConfigChecks() {}

    static void register(Map<String, Consumer<RuleChecks.Ctx>> checks) {
        checks.put("config_keep_vanilla_hunger", ConfigChecks::keepVanillaHunger);
        checks.put("config_recovery_timing", ConfigChecks::recoveryTiming);
        checks.put("config_starting_slots", ConfigChecks::startingSlots);
        checks.put("config_max_meal_slots", ConfigChecks::maxMealSlots);
        checks.put("config_keep_meals_on_death", ConfigChecks::keepMealsOnDeath);
        checks.put("config_duration_multiplier", ConfigChecks::durationMultiplier);
        checks.put("config_tier_thresholds", ConfigChecks::tierThresholds);
    }

    /** Vanilla hunger back: sprinting drains the bar, eating fills it, Recovery is off; meals still come with food. */
    private static void keepVanillaHunger(RuleChecks.Ctx c) {
        var keep = new FullCourseConfig.Snapshot(true, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), D.startingMealSlots(),
                D.maxMealSlots(), D.keepMealsOnDeath(), D.durationMultiplier(), D.tierThresholds());
        FullCourseConfig.with(keep, () -> {
            var food = c.player().getFoodData();
            for (int i = 0; i < 600; i++) {
                food.addExhaustion(0.3F);
                food.tick(c.player());
            }
            check(food.getFoodLevel() < 20, "food level %s after sprinting with vanilla hunger, expected it to drop", food.getFoodLevel());
            food.setFoodLevel(10);
            food.eat(6, 1.2F);
            check(food.getFoodLevel() == 16, "FoodData.eat filled the bar to %s, expected 16", food.getFoodLevel());
            long t = c.t();
            hurtAt(c, t);
            ticks(c, t + 1, t + 400);
            health(c, 10F, "Recovery is off with vanilla hunger");
            // with a full bar, vanilla regeneration heals instead
            food.setFoodLevel(20);
            food.setSaturation(5F);
            for (int i = 0; i < 200; i++) food.tick(c.player());
            check(c.player().getHealth() > 10F, "health %s after a full bar with vanilla hunger, expected vanilla regeneration", c.player().getHealth());
            c.player().setHealth(10F);
            eat(c, Items.BREAD, t + 400);
            check(dishes(c).equals(List.of(id(Items.BREAD))), "meals still come with vanilla hunger: %s", dishes(c));
        });
    }

    /** A 2 s delay and a 1 s interval: the first heart back 2 s after the hit, the next 1 s later. */
    private static void recoveryTiming(RuleChecks.Ctx c) {
        var fast = new FullCourseConfig.Snapshot(false, 2, 20, D.startingMealSlots(), D.maxMealSlots(), D.keepMealsOnDeath(),
                D.durationMultiplier(), D.tierThresholds());
        FullCourseConfig.with(fast, () -> {
            long t = c.t();
            hurtAt(c, t);
            ticks(c, t + 1, t + 39);
            health(c, 10F, "just before 2 s");
            FoodRules.tick(c.player(), t + 40);
            health(c, 11F, "2 s after the hit");
            ticks(c, t + 41, t + 59);
            health(c, 11F, "one interval is 1 s");
            FoodRules.tick(c.player(), t + 60);
            health(c, 12F, "3 s after the hit");
        });
    }

    /** Three starting slots: a new player holds three meals. */
    private static void startingSlots(RuleChecks.Ctx c) {
        var three = new FullCourseConfig.Snapshot(false, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), 3, D.maxMealSlots(),
                D.keepMealsOnDeath(), D.durationMultiplier(), D.tierThresholds());
        FullCourseConfig.with(three, () -> {
            check(state(c).slots() == 3, "slots %s, expected 3", state(c).slots());
            long t = c.t();
            eat(c, Items.COOKED_BEEF, t);
            eat(c, Items.BREAD, t + 100);
            eat(c, Items.BAKED_POTATO, t + 200);
            check(dishes(c).size() == 3, "three meals held: %s", dishes(c));
        });
    }

    /** A maximum of three: slots unlocked past it don't count, and a fourth meal still leaves three held. */
    private static void maxMealSlots(RuleChecks.Ctx c) {
        var three = new FullCourseConfig.Snapshot(false, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), D.startingMealSlots(), 3,
                D.keepMealsOnDeath(), D.durationMultiplier(), D.tierThresholds());
        FullCourseConfig.with(three, () -> {
            Services.PLATFORM.setFoodState(c.player(), FoodState.EMPTY.withBonusSlots(5));
            check(state(c).slots() == 3, "slots %s with five unlocked past a maximum of 3, expected 3", state(c).slots());
            long t = c.t();
            eat(c, Items.COOKED_BEEF, t);
            eat(c, Items.BREAD, t + 100);
            eat(c, Items.BAKED_POTATO, t + 200);
            eat(c, Items.COOKED_CHICKEN, t + 300);
            check(dishes(c).size() == 3, "three meals held at a maximum of 3: %s", dishes(c));
            Services.PLATFORM.setFoodState(c.player(), FoodState.EMPTY);
        });
    }

    private static void keepMealsOnDeath(RuleChecks.Ctx c) {
        var keep = new FullCourseConfig.Snapshot(false, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), D.startingMealSlots(),
                D.maxMealSlots(), true, D.durationMultiplier(), D.tierThresholds());
        FullCourseConfig.with(keep, () -> {
            eat(c, Items.COOKED_BEEF, c.t());
            FoodRules.onDeath(c.player());
            check(dishes(c).equals(List.of(id(Items.COOKED_BEEF))), "meals kept on death: %s", dishes(c));
        });
    }

    /** Twice the duration: bread's meal lasts twice its default time. */
    private static void durationMultiplier(RuleChecks.Ctx c) {
        var twice = new FullCourseConfig.Snapshot(false, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), D.startingMealSlots(),
                D.maxMealSlots(), D.keepMealsOnDeath(), 2.0, D.tierThresholds());
        FullCourseConfig.with(twice, () -> {
            long t = c.t();
            eat(c, Items.BREAD, t);
            long length = state(c).meals().active().getFirst().end() - t;
            check(length == Tier.SIMPLE.durationTicks(2.0) && length == 2L * Tier.SIMPLE.durationTicks(1.0),
                    "bread's meal lasts %s ticks, expected %s", length, 2L * Tier.SIMPLE.durationTicks(1.0));
        });
    }

    /** Thresholds out of reach: foods rated by their score drop to Raw, and come back once the defaults return. */
    private static void tierThresholds(RuleChecks.Ctx c) {
        var high = new FullCourseConfig.Snapshot(false, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), D.startingMealSlots(),
                D.maxMealSlots(), D.keepMealsOnDeath(), D.durationMultiplier(), new int[] {100, 101, 102, 103, 104});
        try {
            FullCourseConfig.with(high, () -> {
                FoodProfiles.reset();
                Tier tier = profile(c, Items.COOKED_BEEF).tier();
                check(tier == Tier.RAW, "cooked beef is %s with thresholds from 100, expected Raw", tier);
            });
        } finally {
            FoodProfiles.reset();
        }
        Tier tier = profile(c, Items.COOKED_BEEF).tier();
        check(tier == Tier.SIMPLE, "cooked beef is %s again with the default thresholds, expected Simple", tier);
    }
}
