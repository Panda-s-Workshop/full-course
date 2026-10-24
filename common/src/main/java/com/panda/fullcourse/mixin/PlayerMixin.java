package com.panda.fullcourse.mixin;

import com.panda.fullcourse.FullCourseConfig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Any food can be eaten at any time: there's no hunger to wait for. */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
    private void fullcourse$alwaysEat(boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
        if (!FullCourseConfig.keepVanillaHunger()) cir.setReturnValue(true);
    }
}
