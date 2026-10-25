package com.panda.fullcourse.kitchen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.fullcourse.food.FoodTags;
import com.panda.fullcourse.logic.IngredientMatch;
import com.panda.fullcourse.registry.ModItems;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
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
 * {@code fullcourse:cooking}: up to six ingredients, shapeless, plus an optional container the dish is served
 * in, cooked over heat for {@code time} ticks. {@code soup_color} ("#RRGGBB") tints the soup in the pot while it cooks.
 */
public record CookingRecipe(List<Ingredient> ingredients, Optional<Ingredient> container, ItemStackTemplate result, int time,
                            Optional<Integer> soupColor)
        implements Recipe<CookingInput> {
    public static final int MAX_INGREDIENTS = 6;
    public static final int DEFAULT_TIME = 200;
    /** Menu data travels as shorts: longer times would wrap on the client's progress arrow. */
    public static final int MAX_TIME = Short.MAX_VALUE;

    public static final MapCodec<CookingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.listOf(1, MAX_INGREDIENTS).fieldOf("ingredients").forGetter(CookingRecipe::ingredients),
            Ingredient.CODEC.optionalFieldOf("container").forGetter(CookingRecipe::container),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(CookingRecipe::result),
            Codec.intRange(1, MAX_TIME).optionalFieldOf("time", DEFAULT_TIME).forGetter(CookingRecipe::time),
            ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("soup_color").forGetter(CookingRecipe::soupColor)
    ).apply(i, CookingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CookingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INGREDIENTS)), CookingRecipe::ingredients,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, CookingRecipe::container,
            ItemStackTemplate.STREAM_CODEC, CookingRecipe::result,
            ByteBufCodecs.VAR_INT, CookingRecipe::time,
            ByteBufCodecs.optional(ByteBufCodecs.INT), CookingRecipe::soupColor,
            CookingRecipe::new);

    /**
     * For each ingredient, the pot slot it takes from; {@code null} if the pot's contents don't fit this recipe. Slots
     * holding the same item are one pool, and empties ({@code #fullcourse:containers}) may be left over.
     */
    public int[] slotsFor(CookingInput input) {
        return match(ingredients, input);
    }

    /** The kitchen's shapeless matching, shared by the pot, the oven and the crock. */
    public static int[] match(List<Ingredient> ingredients, CookingInput input) {
        return IngredientMatch.match(ingredients, input.ingredients(), Ingredient::test, ItemStack::getCount,
                ItemStack::isSameItemSameComponents, s -> s.is(FoodTags.CONTAINERS));
    }

    /** The ingredients only: the pot checks the container itself, so its screen can say which one is missing. */
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
    public RecipeSerializer<CookingRecipe> getSerializer() {
        return ModRecipes.COOKING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CookingRecipe> getType() {
        return ModRecipes.COOKING.get();
    }

    /** The ingredients only: the container is a vessel, not part of the dish (the scorer ignores containers anyway). */
    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredients);
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(ingredients.stream().map(Ingredient::display).toList(),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(ModItems.COOKING_POT.get())));
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.COOKING_CATEGORY.get();
    }
}
