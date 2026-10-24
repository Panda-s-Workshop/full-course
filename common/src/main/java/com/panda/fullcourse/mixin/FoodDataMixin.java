package com.panda.fullcourse.mixin;

import com.panda.fullcourse.FullCourseConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hunger removed: the food level stays full and exhaustion does nothing, so sprinting always works and other
 * mods reading {@code FoodData} see a healthy player. Vanilla's regeneration and starving live in this tick, so they go
 * with it; Recovery replaces them ({@code FoodRules}).
 */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
    @Shadow
    private int foodLevel;
    @Shadow
    private float saturationLevel;
    @Shadow
    private float exhaustionLevel;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fullcourse$noHunger(ServerPlayer player, CallbackInfo ci) {
        if (FullCourseConfig.keepVanillaHunger()) return;
        foodLevel = 20;
        saturationLevel = 5F;
        exhaustionLevel = 0F;
        ci.cancel();
    }

    /** Eating fills nothing: food heals over time instead, and the Saturation effect heals ({@code FoodRules}). */
    @Inject(method = "eat(IF)V", at = @At("HEAD"), cancellable = true)
    private void fullcourse$noFill(int nutrition, float saturation, CallbackInfo ci) {
        if (!FullCourseConfig.keepVanillaHunger()) ci.cancel();
    }

    @Inject(method = "eat(Lnet/minecraft/world/food/FoodProperties;)V", at = @At("HEAD"), cancellable = true)
    private void fullcourse$noFillFood(FoodProperties food, CallbackInfo ci) {
        if (!FullCourseConfig.keepVanillaHunger()) ci.cancel();
    }
}
