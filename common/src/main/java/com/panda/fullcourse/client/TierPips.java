package com.panda.fullcourse.client;

import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.Profile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * A 5×5 dot in the top-left corner of the icon of anything the server's table says gives a meal (feasts and cake
 * included), in its tier's colour, wherever the game draws item decorations (inventories, chests, the hotbar, recipe
 * viewers). The count and durability bar sit at the bottom, so the corner is free. The player's {@code showTierPips}
 * turns it off.
 */
public final class TierPips {
    public static final int SIZE = 5;
    private static final int EDGE = 0xFF202020;

    private TierPips() {}

    public static void draw(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || !ClientOptions.showTierPips() || !FoodProfiles.clientReady()) return;
        Profile profile = FoodProfiles.client(stack.getItem());
        if (!profile.givesMeal()) return;
        g.fill(x, y, x + SIZE, y + SIZE, EDGE);
        g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, profile.tier().color());
    }
}
