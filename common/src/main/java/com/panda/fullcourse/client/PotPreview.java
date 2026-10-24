package com.panda.fullcourse.client;

import com.panda.fullcourse.food.FoodTags;
import com.panda.fullcourse.kitchen.CookingRecipe;
import com.panda.fullcourse.logic.IngredientMatch;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * What the Cooking Pot's arrow says about the grid: the dish it makes, or the nearest dish and what it still needs
 * ("Beef Stew needs any onion, Broth"), or that something in the grid fits no recipe. Read from the recipes the
 * server sent ({@link KitchenRecipes}), so it follows data packs.
 */
public final class PotPreview {
    private PotPreview() {}

    /** The arrow's lines for these grid items; empty when the grid is empty or no recipe is known. */
    public static List<Component> lines(List<ItemStack> grid) {
        List<RecipeHolder<CookingRecipe>> recipes = KitchenRecipes.of(ModRecipes.COOKING.get());
        if (recipes.isEmpty() || grid.stream().allMatch(s -> s.isEmpty() || s.is(FoodTags.CONTAINERS))) return List.of();
        RecipeHolder<CookingRecipe> nearest = null;
        int[] nearestMissing = null;
        for (RecipeHolder<CookingRecipe> holder : recipes) {
            List<Ingredient> ingredients = holder.value().ingredients();
            int[] missing = IngredientMatch.missing(ingredients, grid, Ingredient::test, ItemStack::getCount,
                    ItemStack::isSameItemSameComponents, s -> s.is(FoodTags.CONTAINERS));
            // a recipe none of the grid counts towards isn't near
            if (missing == null || missing.length == ingredients.size()) continue;
            if (missing.length == 0) return List.of(Component.translatable("gui.fullcourse.pot.makes", holder.value().result().create().getHoverName()));
            if (nearestMissing == null || missing.length < nearestMissing.length) {
                nearest = holder;
                nearestMissing = missing;
            }
        }
        if (nearest == null) return List.of(Component.translatable("gui.fullcourse.pot.strays").withStyle(ChatFormatting.GRAY));
        MutableComponent needs = Component.empty();
        for (int i = 0; i < nearestMissing.length; i++) {
            if (i > 0) needs.append(Component.literal(", "));
            needs.append(name(nearest.value().ingredients().get(nearestMissing[i])));
        }
        return List.of(Component.translatable("gui.fullcourse.pot.missing", nearest.value().result().create().getHoverName(), needs));
    }

    /** A tag's own name when the mod names it ("any onion"), otherwise its first item's. */
    static Component name(Ingredient ingredient) {
        if (ingredient.display() instanceof SlotDisplay.TagSlotDisplay tag) {
            var key = tag.tag().unwrapKey();
            if (key.isPresent()) {
                String lang = "hint.fullcourse.tag." + key.get().location().getNamespace() + "." + key.get().location().getPath().replace('/', '.');
                if (Language.getInstance().has(lang)) return Component.translatable(lang);
            }
        }
        return ingredient.items().findFirst().map(h -> (Component) Component.translatable(h.value().getDescriptionId())).orElse(Component.literal("?"));
    }
}
