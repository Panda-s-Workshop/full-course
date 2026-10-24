package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.FullCourse;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** The kitchen's tags: what cuts, what heats a pot. Packs and other mods join through them. */
public final class KitchenTags {
    /** Knives: what cuts on the Cutting Board. The common tag, so other mods' knives work too. */
    public static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/knife"));
    /** Blocks that heat a Cooking Pot standing on them. One with a {@code lit} property heats only while lit. */
    public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, FullCourse.id("heat_sources"));
    /** Blocks heat rises through to a pot above them (a hopper, so a heated pot can be emptied from below). */
    public static final TagKey<Block> HEAT_CONDUCTORS = TagKey.create(Registries.BLOCK, FullCourse.id("heat_conductors"));

    private KitchenTags() {}
}
