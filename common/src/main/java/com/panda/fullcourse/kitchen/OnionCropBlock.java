package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.registry.ModItems;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/** Onions on farmland: a vanilla crop, planted from the onion itself like a carrot. */
public class OnionCropBlock extends CropBlock {
    public OnionCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.item("onion");
    }
}
