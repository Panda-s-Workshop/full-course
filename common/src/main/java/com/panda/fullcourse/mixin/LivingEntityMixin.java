package com.panda.fullcourse.mixin;

import com.panda.fullcourse.food.HealthMemory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers a player's saved health: the meal modifiers are transient, so at load the max health is still 20 and a
 * player saved at 24 of 24 would come back at 20.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements HealthMemory {
    @Unique
    private float fullcourse$savedHealth;

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void fullcourse$rememberHealth(ValueInput input, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer) fullcourse$savedHealth = input.getFloatOr("Health", 0F);
    }

    @Override
    public float fullcourse$savedHealth() {
        return fullcourse$savedHealth;
    }

    @Override
    public void fullcourse$setSavedHealth(float health) {
        fullcourse$savedHealth = health;
    }
}
