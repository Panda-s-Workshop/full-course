package com.panda.fullcourse.mixin;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.kitchen.FeastBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Each cake slice is a meal: the cake's profile, and vanilla's 2 nutrition as the heal. A cake is shared like a feast:
 * one helping per player every five minutes, and none while Queasy, since eating is never refused for a full bar. With
 * {@code keepVanillaHunger} the bar refuses as in vanilla, so the cake is vanilla's again.
 */
@Mixin(CakeBlock.class)
abstract class CakeBlockMixin {
    @Inject(method = "eat", at = @At("HEAD"), cancellable = true)
    private static void fullcourse$oneHelping(LevelAccessor level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof ServerPlayer server) || FullCourseConfig.keepVanillaHunger()) return;
        Component refused = FeastBlock.refusal(server, Items.CAKE);
        if (refused == null) return;
        server.sendOverlayMessage(refused);
        // FAIL, not SUCCESS: a candle cake drops its candle whenever eating consumed the click
        cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "eat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(IF)V"))
    private static void fullcourse$slice(LevelAccessor level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer server) FoodRules.onEatDish(server, Items.CAKE, FoodRules.CAKE_SLICE_NUTRITION);
    }
}
