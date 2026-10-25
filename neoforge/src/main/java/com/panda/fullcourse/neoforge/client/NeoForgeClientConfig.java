package com.panda.fullcourse.neoforge.client;

import com.panda.fullcourse.client.ClientOptions;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Each player's display settings as a NeoForge client config ({@code config/fullcourse-client.toml}). */
final class NeoForgeClientConfig implements ClientOptions.Source {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ClientOptions.Values D = ClientOptions.DEFAULTS;

    private static final ModConfigSpec.EnumValue<ClientOptions.Anchor> MEAL_ROW_ANCHOR = B
            .comment("Where the meal row sits: HOTBAR (where the food bar was, with the rows above it lifted to make room),",
                    "TOP_LEFT, TOP, TOP_RIGHT, LEFT, RIGHT, BOTTOM_LEFT or BOTTOM_RIGHT.")
            .translation("fullcourse.configuration.mealRowAnchor")
            .defineEnum("mealRowAnchor", D.mealRowAnchor());
    private static final ModConfigSpec.IntValue MEAL_ROW_X = B
            .comment("GUI pixels to move the meal row right (negative: left) from its anchor. It stays on screen.")
            .translation("fullcourse.configuration.mealRowX")
            .defineInRange("mealRowX", D.mealRowX(), -ClientOptions.MAX_SHIFT, ClientOptions.MAX_SHIFT);
    private static final ModConfigSpec.IntValue MEAL_ROW_Y = B
            .comment("GUI pixels to move the meal row down (negative: up) from its anchor. On the hotbar, a negative value",
                    "lifts the row and everything stacked above it, to make room for another mod's HUD row.")
            .translation("fullcourse.configuration.mealRowY")
            .defineInRange("mealRowY", D.mealRowY(), -ClientOptions.MAX_SHIFT, ClientOptions.MAX_SHIFT);
    private static final ModConfigSpec.EnumValue<ClientOptions.ListSide> MEAL_LIST = B
            .comment("Where the meal list sits beside the survival inventory: LEFT, RIGHT or OFF.")
            .translation("fullcourse.configuration.mealList")
            .defineEnum("mealList", D.mealList());
    private static final ModConfigSpec.IntValue MEAL_LIST_X = B
            .comment("GUI pixels to move the meal list right (negative: left). It stays on screen.")
            .translation("fullcourse.configuration.mealListX")
            .defineInRange("mealListX", D.mealListX(), -ClientOptions.MAX_SHIFT, ClientOptions.MAX_SHIFT);
    private static final ModConfigSpec.IntValue MEAL_LIST_Y = B
            .comment("GUI pixels to move the meal list down (negative: up). It stays on screen.")
            .translation("fullcourse.configuration.mealListY")
            .defineInRange("mealListY", D.mealListY(), -ClientOptions.MAX_SHIFT, ClientOptions.MAX_SHIFT);
    private static final ModConfigSpec.BooleanValue SHOW_RECOVERY_PARTICLES = B
            .comment("Show a heart each time Recovery heals you.")
            .translation("fullcourse.configuration.showRecoveryParticles")
            .define("showRecoveryParticles", D.showRecoveryParticles());
    private static final ModConfigSpec.BooleanValue SHOW_TIER_PIPS = B
            .comment("A dot in the tier's colour on the icon of every food that gives a meal.")
            .translation("fullcourse.configuration.showTierPips")
            .define("showTierPips", D.showTierPips());

    static final ModConfigSpec SPEC = B.build();

    @Override public ClientOptions.Anchor mealRowAnchor() { return SPEC.isLoaded() ? MEAL_ROW_ANCHOR.get() : D.mealRowAnchor(); }
    @Override public int mealRowX() { return SPEC.isLoaded() ? MEAL_ROW_X.get() : D.mealRowX(); }
    @Override public int mealRowY() { return SPEC.isLoaded() ? MEAL_ROW_Y.get() : D.mealRowY(); }
    @Override public ClientOptions.ListSide mealList() { return SPEC.isLoaded() ? MEAL_LIST.get() : D.mealList(); }
    @Override public int mealListX() { return SPEC.isLoaded() ? MEAL_LIST_X.get() : D.mealListX(); }
    @Override public int mealListY() { return SPEC.isLoaded() ? MEAL_LIST_Y.get() : D.mealListY(); }
    @Override public boolean showRecoveryParticles() { return SPEC.isLoaded() ? SHOW_RECOVERY_PARTICLES.get() : D.showRecoveryParticles(); }
    @Override public boolean showTierPips() { return SPEC.isLoaded() ? SHOW_TIER_PIPS.get() : D.showTierPips(); }
}
