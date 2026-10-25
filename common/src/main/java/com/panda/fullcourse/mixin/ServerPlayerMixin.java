package com.panda.fullcourse.mixin;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.HealthMemory;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaving the End makes a new player that keeps everything; its health was clamped before the meal bonus came back.
 * A respawn after death with {@code keepMealsOnDeath} starts at the old max health, before the kept meals' bonus is
 * back: it fills up once they are.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void fullcourse$rememberHealth(ServerPlayer old, boolean keepEverything, CallbackInfo ci) {
        if (keepEverything) {
            ((HealthMemory) this).fullcourse$setSavedHealth(old.getHealth());
            ((ServerPlayer) (Object) this).lastDamageStamp = old.lastDamageStamp; // Recovery's delay runs on
        }
        else if (FullCourseConfig.keepMealsOnDeath()) ((HealthMemory) this).fullcourse$setSavedHealth(Float.MAX_VALUE);
    }
}
