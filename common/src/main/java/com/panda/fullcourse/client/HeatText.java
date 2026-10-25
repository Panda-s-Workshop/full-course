package com.panda.fullcourse.client;

import com.panda.fullcourse.kitchen.KitchenHeat;
import com.panda.fullcourse.kitchen.KitchenTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;

/**
 * What heats a cooking pot, read from {@code #fullcourse:heat_sources} and {@code #fullcourse:heat_conductors} as the
 * server sent them, so a pack's own heat sources are named too; then the lines other mods add ({@link KitchenHeat}).
 */
public final class HeatText {
    /** Names listed before "and N more". */
    static final int MAX_NAMES = 12;

    private HeatText() {}

    /** For a tooltip: the names in grey on its dark background. */
    public static List<Component> needsHeat() {
        return needsHeat(ChatFormatting.GRAY);
    }

    /** For a light page (a recipe viewer's information page): the names in dark grey. */
    public static List<Component> needsHeatOnPage() {
        return needsHeat(ChatFormatting.DARK_GRAY);
    }

    private static List<Component> needsHeat(ChatFormatting detail) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.fullcourse.pot.cold"));
        lines.add(names(KitchenTags.HEAT_SOURCES).copy().withStyle(detail));
        List<Block> conductors = blocks(KitchenTags.HEAT_CONDUCTORS);
        if (!conductors.isEmpty()) {
            lines.add(Component.translatable("gui.fullcourse.pot.conducts", names(KitchenTags.HEAT_CONDUCTORS)).withStyle(detail));
        }
        lines.addAll(KitchenHeat.notes());
        return lines;
    }

    static List<Block> blocks(TagKey<Block> tag) {
        List<Block> out = new ArrayList<>();
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) out.add(holder.value());
        return out;
    }

    /**
     * The tag's blocks by name, "A, B, C", or "A, B, … and 3 more" past {@link #MAX_NAMES}. A dyed copy of a block
     * already named (Cooking for Blockheads' Orange Oven beside its Oven) isn't named again.
     */
    static Component names(TagKey<Block> tag) {
        List<Component> names = new ArrayList<>();
        for (Block block : blocks(tag)) {
            Component name = block.getName();
            if (names.stream().noneMatch(n -> n.getString().equals(name.getString()))) names.add(name);
        }
        List<String> plain = names.stream().map(Component::getString).toList();
        names.removeIf(n -> dyedCopy(n.getString(), plain));
        if (names.size() <= MAX_NAMES) return ComponentUtils.formatList(names, Component.literal(", "));
        Component shown = ComponentUtils.formatList(names.subList(0, MAX_NAMES), Component.literal(", "));
        return Component.translatable("gui.fullcourse.pot.more", shown, names.size() - MAX_NAMES);
    }

    /** "Orange Oven" when "Oven" is among {@code names}: a colour's name, a space, then a name listed on its own. */
    static boolean dyedCopy(String name, List<String> names) {
        for (DyeColor dye : DyeColor.values()) {
            String colour = Component.translatable("color.minecraft." + dye.getName()).getString() + " ";
            if (name.startsWith(colour) && names.contains(name.substring(colour.length()))) return true;
        }
        return false;
    }
}
