package com.panda.fullcourse.mixin;

import com.panda.fullcourse.food.FoodRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player ate: heal over time and fill a meal slot. Runs before the stack shrinks, so the item is still there. */
@Mixin(FoodProperties.class)
abstract class FoodPropertiesMixin {
    @Inject(method = "onConsume", at = @At("HEAD"))
    private void fullcourse$onEat(Level level, LivingEntity entity, ItemStack stack, Consumable consumable, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) FoodRules.onEat(player, stack, (FoodProperties) (Object) this);
    }
}
