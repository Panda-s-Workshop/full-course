package com.panda.fullcourse.mixin;

import com.panda.fullcourse.kitchen.FeastBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Farmer's Delight's pies are eaten a bite at a time where they stand, and eating is never refused for a full bar: one
 * helping per player every five minutes, as for a feast or cake. Applies only when Farmer's Delight is installed.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.PieBlock")
abstract class FarmersDelightPieMixin {
    @Inject(method = "consumeBite", at = @At("HEAD"), cancellable = true, require = 0)
    private void fullcourse$oneHelping(Level level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof ServerPlayer server)) return;
        Component refused = FeastBlock.refusal(server, ((Block) (Object) this).asItem());
        if (refused == null) return;
        server.sendOverlayMessage(refused);
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
