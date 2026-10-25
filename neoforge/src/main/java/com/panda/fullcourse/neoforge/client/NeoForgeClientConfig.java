package com.panda.fullcourse.neoforge.client;

import com.panda.fullcourse.client.ClientOptions;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Each player's display settings as a NeoForge client config ({@code config/fullcourse-client.toml}). */
final class NeoForgeClientConfig implements ClientOptions.Source {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue MEAL_ROW_OFFSET = B
            .comment("GUI pixels to lift the meal row by, to make room for another mod's HUD row.")
            .defineInRange("mealRowOffset", ClientOptions.DEFAULTS.mealRowOffset(), 0, ClientOptions.MAX_OFFSET);
    private static final ModConfigSpec.BooleanValue SHOW_RECOVERY_PARTICLES = B
            .comment("Show a heart each time Recovery heals you.")
            .define("showRecoveryParticles", ClientOptions.DEFAULTS.showRecoveryParticles());
    private static final ModConfigSpec.BooleanValue SHOW_TIER_PIPS = B
            .comment("A dot in the tier's colour on the icon of every food that gives a meal.")
            .define("showTierPips", ClientOptions.DEFAULTS.showTierPips());

    static final ModConfigSpec SPEC = B.build();

    @Override public int mealRowOffset() { return SPEC.isLoaded() ? MEAL_ROW_OFFSET.get() : ClientOptions.DEFAULTS.mealRowOffset(); }
    @Override public boolean showRecoveryParticles() { return SPEC.isLoaded() ? SHOW_RECOVERY_PARTICLES.get() : ClientOptions.DEFAULTS.showRecoveryParticles(); }
    @Override public boolean showTierPips() { return SPEC.isLoaded() ? SHOW_TIER_PIPS.get() : ClientOptions.DEFAULTS.showTierPips(); }
}
