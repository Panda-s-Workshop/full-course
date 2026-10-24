package com.panda.fullcourse.client.compat.cfb;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.food.FoodProfiles;
import java.util.Comparator;
import net.blay09.mods.cookingforblockheads.api.ISortButton;
import net.blay09.mods.cookingforblockheads.crafting.CraftableWithStatus;
import net.blay09.mods.cookingforblockheads.menu.comparator.ComparatorName;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * A sort button for Cooking for Blockheads' kitchen screens: the best dishes first, by their Full Course tier, then by
 * name. Its hunger and saturation sorts mean little without a food bar. The icon is a 20 x 20 button in three states
 * (normal, hovered, inactive) stacked in {@code textures/gui/cfb_sort.png}.
 */
public final class TierSortButton implements ISortButton {
    private static final Identifier ID = FullCourse.id("tier");
    private static final Identifier ICON = FullCourse.id("textures/gui/cfb_sort.png");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public Identifier getIcon() {
        return ICON;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("gui.fullcourse.sort_by_tier");
    }

    @Override
    public Comparator<CraftableWithStatus> getComparator(Player player) {
        Comparator<CraftableWithStatus> byTier = Comparator.comparingInt(c -> -FoodProfiles.client(c.itemStack().getItem()).tier().stars());
        return byTier.thenComparing(new ComparatorName());
    }

    @Override
    public int getIconTextureX() {
        return 0;
    }

    @Override
    public int getIconTextureY() {
        return 0;
    }
}
