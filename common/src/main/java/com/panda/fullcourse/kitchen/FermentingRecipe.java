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
 * {@code fullcourse:fermenting}: up to four ingredients sealed in a Fermenting Crock for {@code time} ticks
 * (half a day by default). The crock repeats the recipe while its stacks allow.
 */
public record FermentingRecipe(List<Ingredient> ingredients, ItemStackTemplate result, int time) implements Recipe<CookingInput> {
    public static final int MAX_INGREDIENTS = 4;
    public static final int DEFAULT_TIME = 12000;

    public static final MapCodec<FermentingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.listOf(1, MAX_INGREDIENTS).fieldOf("ingredients").forGetter(FermentingRecipe::ingredients),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(FermentingRecipe::result),
            Codec.intRange(1, 1_000_000).optionalFieldOf("time", DEFAULT_TIME).forGetter(FermentingRecipe::time)
    ).apply(i, FermentingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FermentingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INGREDIENTS)), FermentingRecipe::ingredients,
            ItemStackTemplate.STREAM_CODEC, FermentingRecipe::result,
            ByteBufCodecs.VAR_INT, FermentingRecipe::time,
            FermentingRecipe::new);

    /** For each ingredient, the crock slot it takes from; {@code null} if the crock's contents don't fit this recipe. */
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
    public RecipeSerializer<FermentingRecipe> getSerializer() {
        return ModRecipes.FERMENTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<FermentingRecipe> getType() {
        return ModRecipes.FERMENTING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredients);
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(ingredients.stream().map(Ingredient::display).toList(),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(ModItems.FERMENTING_CROCK.get())));
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.FERMENTING_CATEGORY.get();
    }
}
