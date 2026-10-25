package com.panda.fullcourse.neoforge;

import com.panda.fullcourse.FullCourseConfig;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The mod's settings as a NeoForge config (synced from server to clients). */
public final class NeoForgeConfig implements FullCourseConfig.Source {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final FullCourseConfig.Source D = FullCourseConfig.DEFAULTS;

    private static final ModConfigSpec.BooleanValue KEEP_VANILLA_HUNGER = B
            .comment("Keep vanilla hunger and regeneration; Full Course only adds meal benefits.")
            .translation("fullcourse.configuration.keepVanillaHunger")
            .worldRestart()
            .define("keepVanillaHunger", D.keepVanillaHunger());
    private static final ModConfigSpec.IntValue RECOVERY_DELAY = B
            .comment("Seconds without taking damage before Recovery starts healing.")
            .translation("fullcourse.configuration.recoveryDelaySeconds")
            .worldRestart()
            .defineInRange("recoveryDelaySeconds", D.recoveryDelaySeconds(), 0, 600);
    private static final ModConfigSpec.IntValue RECOVERY_INTERVAL = B
            .comment("Ticks between Recovery heals of 1 HP, before Mending.")
            .translation("fullcourse.configuration.recoveryIntervalTicks")
            .worldRestart()
            .defineInRange("recoveryIntervalTicks", D.recoveryIntervalTicks(), 1, 12000);
    private static final ModConfigSpec.IntValue STARTING_SLOTS = B
            .comment("Meal slots a new player has.")
            .translation("fullcourse.configuration.startingMealSlots")
            .worldRestart()
            .defineInRange("startingMealSlots", D.startingMealSlots(), 1, 16);
    private static final ModConfigSpec.IntValue MAX_SLOTS = B
            .comment("The most meal slots a player can have. The Tasting Journal opens three past the starting slots; /fullcourse unlock can go up to this.")
            .translation("fullcourse.configuration.maxMealSlots")
            .worldRestart()
            .defineInRange("maxMealSlots", D.maxMealSlots(), 1, 16);
    private static final ModConfigSpec.BooleanValue KEEP_MEALS = B
            .comment("Keep held meals through death.")
            .translation("fullcourse.configuration.keepMealsOnDeath")
            .worldRestart()
            .define("keepMealsOnDeath", D.keepMealsOnDeath());
    private static final ModConfigSpec.DoubleValue DURATION = B
            .comment("Multiplies every meal's duration.")
            .translation("fullcourse.configuration.durationMultiplier")
            .worldRestart()
            .defineInRange("durationMultiplier", D.durationMultiplier(), 0.05, 20.0);
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> THRESHOLDS = B
            .comment("Lowest recipe-chain score of tiers 1 to 5 (Simple, Prepared, Hearty, Gourmet, Feast): exactly five, ascending.")
            .translation("fullcourse.configuration.tierThresholds")
            .worldRestart()
            .defineList(List.of("tierThresholds"), () -> List.of(1, 3, 5, 8, 12), () -> 1, o -> o instanceof Integer i && i >= 1 && i < 1000,
                    ModConfigSpec.Range.of(5, 5));
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> MILESTONES = B
            .comment("Different dishes to taste for meal slot 3 (two stars or more), 4 and 5 (three stars or more; 5 also a feast): exactly three.")
            .translation("fullcourse.configuration.slotMilestones")
            .worldRestart()
            .defineList(List.of("slotMilestones"), () -> List.of(8, 15, 20), () -> 8, o -> o instanceof Integer i && i >= 1 && i < 10000,
                    ModConfigSpec.Range.of(3, 3));

    public static final ModConfigSpec SPEC = B.build();

    @Override public boolean keepVanillaHunger() { return SPEC.isLoaded() ? KEEP_VANILLA_HUNGER.get() : D.keepVanillaHunger(); }
    @Override public int recoveryDelaySeconds() { return SPEC.isLoaded() ? RECOVERY_DELAY.get() : D.recoveryDelaySeconds(); }
    @Override public int recoveryIntervalTicks() { return SPEC.isLoaded() ? RECOVERY_INTERVAL.get() : D.recoveryIntervalTicks(); }
    @Override public int startingMealSlots() { return SPEC.isLoaded() ? STARTING_SLOTS.get() : D.startingMealSlots(); }
    @Override public int maxMealSlots() { return SPEC.isLoaded() ? MAX_SLOTS.get() : D.maxMealSlots(); }
    @Override public boolean keepMealsOnDeath() { return SPEC.isLoaded() ? KEEP_MEALS.get() : D.keepMealsOnDeath(); }
    @Override public double durationMultiplier() { return SPEC.isLoaded() ? DURATION.get() : D.durationMultiplier(); }

    @Override
    public int[] tierThresholds() {
        if (!SPEC.isLoaded()) return D.tierThresholds();
        return THRESHOLDS.get().stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public int[] slotMilestones() {
        if (!SPEC.isLoaded()) return D.slotMilestones();
        return MILESTONES.get().stream().mapToInt(Integer::intValue).toArray();
    }
}
