package com.panda.fullcourse.compat;

import com.panda.fullcourse.FullCourse;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dishes served from a block, as Farmer's Delight's feasts and pies are and its add-ons' built on them: each serving
 * item, mapped to the block's item. Read from the block's own {@code getServingItem(BlockState)} (over each of its
 * states, as a medley serves several) and {@code getPieSliceItem()}, reflectively, so nothing here needs the other mod
 * and an add-on's feast needs no data.
 */
public final class ServedFrom {
    private ServedFrom() {}

    public static Map<Item, Item> servings() {
        Map<Item, Item> out = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem blockItem)) continue;
            Block block = blockItem.getBlock();
            if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(FullCourse.MODID)) continue; // ours give meals as blocks
            for (Item serving : servingsOf(block)) out.putIfAbsent(serving, item);
        }
        return out;
    }

    static Set<Item> servingsOf(Block block) {
        Set<Item> out = new LinkedHashSet<>();
        for (Method m : block.getClass().getMethods()) {
            boolean perState = m.getName().equals("getServingItem") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == BlockState.class;
            boolean slice = m.getName().equals("getPieSliceItem") && m.getParameterCount() == 0;
            if (!(perState || slice) || !ItemStack.class.isAssignableFrom(m.getReturnType())) continue;
            if (perState) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) add(out, m, block, state);
            } else {
                add(out, m, block);
            }
        }
        out.remove(block.asItem());
        return out;
    }

    private static void add(Set<Item> out, Method m, Block block, Object... args) {
        try {
            if (m.invoke(block, args) instanceof ItemStack stack && !stack.isEmpty()) out.add(stack.getItem());
        } catch (ReflectiveOperationException | RuntimeException e) {
            // a state the block won't serve from
        }
    }
}
