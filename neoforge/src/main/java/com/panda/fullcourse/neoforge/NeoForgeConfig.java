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
            .define("keepVanillaHunger", D.keepVanillaHunger());
    private static final ModConfigSpec.IntValue RECOVERY_DELAY = B
            .comment("Seconds without taking damage before Recovery starts healing.")
            .defineInRange("recoveryDelaySeconds", D.recoveryDelaySeconds(), 0, 600);
    private static final ModConfigSpec.IntValue RECOVERY_INTERVAL = B
            .comment("Ticks between Recovery heals of 1 HP, before Mending.")
            .defineInRange("recoveryIntervalTicks", D.recoveryIntervalTicks(), 1, 12000);
    private static final ModConfigSpec.IntValue STARTING_SLOTS = B
            .comment("Meal slots a new player has.")
            .defineInRange("startingMealSlots", D.startingMealSlots(), 1, 16);
    private static final ModConfigSpec.IntValue MAX_SLOTS = B
            .comment("The most meal slots the Tasting Journal can unlock.")
            .defineInRange("maxMealSlots", D.maxMealSlots(), 1, 16);
    private static final ModConfigSpec.BooleanValue KEEP_MEALS = B
            .comment("Keep held meals through death.")
            .define("keepMealsOnDeath", D.keepMealsOnDeath());
    private static final ModConfigSpec.DoubleValue DURATION = B
            .comment("Multiplies every meal's duration.")
            .defineInRange("durationMultiplier", D.durationMultiplier(), 0.05, 20.0);
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> THRESHOLDS = B
            .comment("Lowest recipe-chain score of tiers 1 to 5 (Simple, Prepared, Hearty, Gourmet, Feast): exactly five, ascending.")
            .defineList(List.of("tierThresholds"), () -> List.of(1, 3, 5, 8, 12), () -> 1, o -> o instanceof Integer i && i >= 0 && i < 1000,
                    ModConfigSpec.Range.of(5, 5));

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
}
