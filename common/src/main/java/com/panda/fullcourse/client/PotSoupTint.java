package com.panda.fullcourse.client;

import com.panda.fullcourse.kitchen.CookingPotBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The cooking pot's soup takes the colour of the dish cooking in it (the recipe's {@code soup_color}). */
public final class PotSoupTint implements BlockTintSource {
    @Override
    public int color(BlockState state) {
        return 0xFF000000 | CookingPotBlockEntity.DEFAULT_SOUP;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int rgb = level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot ? pot.soupColor() : CookingPotBlockEntity.DEFAULT_SOUP;
        return 0xFF000000 | rgb;
    }

    /** Breaking particles show the pot's iron, untinted. */
    @Override
    public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return 0xFFFFFFFF;
    }
}
