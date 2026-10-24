package com.panda.fullcourse.kitchen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.fullcourse.registry.ModItems;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/**
 * {@code fullcourse:baking}: up to four ingredients, shapeless, baked in a lit Brick Oven for {@code time}
 * ticks. Matching is the pot's: one stack feeds repeats, a stray item stops the recipe.
 */
public record BakingRecipe(List<Ingredient> ingredients, ItemStackTemplate result, int time) implements Recipe<CookingInput> {
    public static final int MAX_INGREDIENTS = 4;
    public static final int DEFAULT_TIME = 300;

    public static final MapCodec<BakingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.listOf(1, MAX_INGREDIENTS).fieldOf("ingredients").forGetter(BakingRecipe::ingredients),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(BakingRecipe::result),
            Codec.intRange(1, CookingRecipe.MAX_TIME).optionalFieldOf("time", DEFAULT_TIME).forGetter(BakingRecipe::time)
    ).apply(i, BakingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BakingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INGREDIENTS)), BakingRecipe::ingredients,
            ItemStackTemplate.STREAM_CODEC, BakingRecipe::result,
            ByteBufCodecs.VAR_INT, BakingRecipe::time,
            BakingRecipe::new);

    /** For each ingredient, the oven slot it takes from; {@code null} if the oven's contents don't fit this recipe. */
    public int[] slotsFor(CookingInput input) {
        return CookingRecipe.match(ingredients, input);
    }

    @Override
    public boolean matches(CookingInput input, Level level) {
        return slotsFor(input) != null;
    }

    @Override
    public ItemStack assemble(CookingInput input) {
        return result.create();
    }

    @Override
    public boolean showNotification() {
        return true;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<BakingRecipe> getSerializer() {
        return ModRecipes.BAKING_SERIALIZER.get();
    }

    @Override
    public RecipeType<BakingRecipe> getType() {
        return ModRecipes.BAKING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredients);
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(ingredients.stream().map(Ingredient::display).toList(),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(ModItems.BRICK_OVEN.get())));
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.BAKING_CATEGORY.get();
    }
}
