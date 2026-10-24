package com.panda.fullcourse.compat;

import com.panda.fullcourse.kitchen.KitchenHeat;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;

/**
 * Cooking for Blockheads: its oven heats a Cooking Pot set on top while it burns fuel (the oven is in
 * {@code #fullcourse:heat_sources} through its own block tag). Its Cooking Table lists our crafted dishes by itself,
 * since they are food; our crafted ingredients join its {@code ingredients} tag in data. Only loaded when the mod is.
 */
public final class CookingForBlockheadsCompat {
    private CookingForBlockheadsCompat() {}

    public static void init() {
        KitchenHeat.register(OvenBlockEntity.class, OvenBlockEntity::isBurning);
    }
}
