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
import com.panda.fullcourse.food.FoodText;
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
            // food fills the bar rather than healing over time, and Mending has no Recovery to speed up: the tooltip says so
            List<String> stew = FoodText.lines(profile(c, Items.RABBIT_STEW), 10, false, false).stream().map(net.minecraft.network.chat.Component::getString).toList();
            check(stew.stream().noneMatch(l -> l.contains("Heals")), "no heal line with vanilla hunger: %s", stew);
            check(stew.stream().anyMatch(l -> l.startsWith("Mending II") && l.endsWith("does nothing with vanilla hunger")), "Mending marked off: %s", stew);
            // the bar decides when cake is eaten, as in vanilla: no helping rule, two slices in a row for a hungry player
            var pos = c.spot();
            var before = c.level().getBlockState(pos);
            c.level().setBlock(pos, net.minecraft.world.level.block.Blocks.CAKE.defaultBlockState(), 3);
            try {
                food.setFoodLevel(10);
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
                for (int i = 0; i < 2; i++) c.level().getBlockState(pos).useWithoutItem(c.level(), c.player(), hit);
                int bites = c.level().getBlockState(pos).getValue(net.minecraft.world.level.block.CakeBlock.BITES);
                check(bites == 2, "a hungry player with vanilla hunger eats two slices in a row, bites %s", bites);
            } finally {
                c.level().setBlock(pos, before, 3);
            }
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

    /** Three starting slots: a new player holds three meals; back at two, the next tick drops the one with least time. */
    private static void startingSlots(RuleChecks.Ctx c) {
        var three = new FullCourseConfig.Snapshot(false, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), 3, D.maxMealSlots(),
                D.keepMealsOnDeath(), D.durationMultiplier(), D.tierThresholds());
        long t = c.t();
        FullCourseConfig.with(three, () -> {
            check(state(c).slots() == 3, "slots %s, expected 3", state(c).slots());
            eat(c, Items.COOKED_BEEF, t);
            eat(c, Items.BREAD, t + 100);
            eat(c, Items.BAKED_POTATO, t + 200);
            check(dishes(c).size() == 3, "three meals held: %s", dishes(c));
        });
        FoodRules.tick(c.player(), t + 201);
        check(dishes(c).size() == 2 && !dishes(c).contains(id(Items.COOKED_BEEF)), "slots lowered to 2: the steak, with least time, goes at once: %s", dishes(c));
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
                check(!FoodProfiles.anyFeast(c.server()), "no food rates Feast from 104: the log warns of it");
            });
        } finally {
            FoodProfiles.reset();
        }
        // a dish whose families a file sets gives no more of them than its tier allows: the banquet at one star gives one
        var low = new FullCourseConfig.Snapshot(false, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), D.startingMealSlots(),
                D.maxMealSlots(), D.keepMealsOnDeath(), D.durationMultiplier(), new int[] {1, 100, 101, 102, 103});
        try {
            FullCourseConfig.with(low, () -> {
                FoodProfiles.reset();
                var banquet = profile(c, com.panda.fullcourse.registry.ModItems.item("roast_banquet"));
                check(banquet.tier() == Tier.SIMPLE, "the banquet is %s with thresholds 1, 100.., expected Simple", banquet.tier());
                check(banquet.families().equals(List.of(com.panda.fullcourse.logic.Family.BRAWN)), "a one-star banquet gives %s, expected its file's first family",
                        banquet.families());
            });
            // low thresholds: a stew's score reaches Feast, but only a placed dish is a feast
            FullCourseConfig.with(new FullCourseConfig.Snapshot(false, D.recoveryDelaySeconds(), D.recoveryIntervalTicks(), D.startingMealSlots(),
                    D.maxMealSlots(), D.keepMealsOnDeath(), D.durationMultiplier(), new int[] {1, 2, 3, 4, 5}), () -> {
                FoodProfiles.reset();
                Tier stew = profile(c, Items.RABBIT_STEW).tier();
                check(stew == Tier.GOURMET, "rabbit stew is %s with thresholds 1..5, expected Gourmet: it isn't placed", stew);
                Tier banquet = profile(c, com.panda.fullcourse.registry.ModItems.item("roast_banquet")).tier();
                check(banquet == Tier.FEAST, "the banquet is %s with thresholds 1..5, expected Feast", banquet);
            });
        } finally {
            FoodProfiles.reset();
        }
        Tier tier = profile(c, Items.COOKED_BEEF).tier();
        check(tier == Tier.SIMPLE, "cooked beef is %s again with the default thresholds, expected Simple", tier);
        check(FoodProfiles.anyFeast(c.server()), "the feasts rate Feast with the default thresholds");
    }
}
