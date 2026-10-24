package com.panda.fullcourse.kitchen;

import com.mojang.serialization.MapCodec;
import com.panda.fullcourse.registry.ModItems;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/** {@code fullcourse:cutting}: one item on the Cutting Board, one knife stroke, the result pops out. */
public class CuttingRecipe extends SingleItemRecipe {
    public static final MapCodec<CuttingRecipe> MAP_CODEC = simpleMapCodec(CuttingRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, CuttingRecipe> STREAM_CODEC = simpleStreamCodec(CuttingRecipe::new);

    public CuttingRecipe(Recipe.CommonInfo info, Ingredient input, ItemStackTemplate result) {
        super(info, input, result);
    }

    public ItemStackTemplate output() {
        return result();
    }

    @Override
    public RecipeSerializer<CuttingRecipe> getSerializer() {
        return ModRecipes.CUTTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CuttingRecipe> getType() {
        return ModRecipes.CUTTING.get();
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new StonecutterRecipeDisplay(input().display(), new SlotDisplay.ItemStackSlotDisplay(result()),
                new SlotDisplay.ItemSlotDisplay(ModItems.CUTTING_BOARD.get())));
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.CUTTING_CATEGORY.get();
    }
}
