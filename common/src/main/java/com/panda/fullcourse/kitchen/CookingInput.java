package com.panda.fullcourse.kitchen;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** What a Cooking Pot holds: up to six ingredient stacks and the container the dish is served in. */
public record CookingInput(List<ItemStack> ingredients, ItemStack container) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return index < ingredients.size() ? ingredients.get(index) : container;
    }

    @Override
    public int size() {
        return ingredients.size() + 1;
    }
}
