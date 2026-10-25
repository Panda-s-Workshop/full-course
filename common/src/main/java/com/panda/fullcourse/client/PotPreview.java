package com.panda.fullcourse.client;

import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodTags;
import com.panda.fullcourse.kitchen.BakingRecipe;
import com.panda.fullcourse.kitchen.CookingRecipe;
import com.panda.fullcourse.kitchen.RecipeHints;
import com.panda.fullcourse.logic.IngredientMatch;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * What the Cooking Pot's and the Brick Oven's arrow says about the grid: the dish it makes (with its stars and what it's
 * served in), or the nearest dish and what it still needs ("Beef Stew needs any onion, Broth", with where an
 * ingredient that's made first comes from), or which item no recipe of that station uses. The nearest dish is the one
 * with the fewest ingredients still to add, the better dish on a tie; a recipe none of the grid counts towards is never
 * nearest. Read from the recipes the server sent ({@link KitchenRecipes}), so it follows data packs; worked out once
 * per grid, not every frame.
 */
public final class PotPreview {
    /** At most this many "where it comes from" lines under a missing list. */
    private static final int MAX_FROM = 3;
    private static final Cache POT = new Cache(), OVEN = new Cache();

    /** A station's recipe as the preview reads it: what it takes, what it makes, what that's served in. */
    public record Dish(List<Ingredient> ingredients, ItemStack result, Optional<Ingredient> container) {
        static Dish of(CookingRecipe r) {
            return new Dish(r.ingredients(), r.result().create(), r.container());
        }

        static Dish of(BakingRecipe r) {
            return new Dish(r.ingredients(), r.result().create(), Optional.empty());
        }
    }

    /** One station's last grid and what it said. */
    private static final class Cache {
        Object recipes;
        List<ItemStack> grid = List.of();
        List<Component> lines = List.of();
    }

    private PotPreview() {}

    /** The pot arrow's lines for these grid items; empty when the grid is empty or no recipe is known. */
    public static List<Component> lines(List<ItemStack> grid) {
        return cached(POT, KitchenRecipes.of(ModRecipes.COOKING.get()), grid, Dish::of, "gui.fullcourse.pot.unused");
    }

    /** The oven arrow's lines, as {@link #lines} for the pot. */
    public static List<Component> ovenLines(List<ItemStack> grid) {
        return cached(OVEN, KitchenRecipes.of(ModRecipes.BAKING.get()), grid, Dish::of, "gui.fullcourse.oven.unused");
    }

    private static <R extends Recipe<?>> List<Component> cached(Cache cache, List<RecipeHolder<R>> recipes, List<ItemStack> grid,
                                                                Function<R, Dish> dish, String unused) {
        if (recipes != cache.recipes || !sameGrid(grid, cache.grid)) {
            cache.lines = dishes(grid, recipes.stream().map(h -> dish.apply(h.value())).toList(), unused);
            cache.recipes = recipes;
            cache.grid = grid.stream().map(ItemStack::copy).toList();
        }
        return cache.lines;
    }

    private static boolean sameGrid(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }

    /** Some pot recipe takes all of these and needs more: the grid is on its way, not wrong. */
    public static boolean incomplete(List<ItemStack> grid) {
        return missing(lines(grid));
    }

    /** As {@link #incomplete} for the oven. */
    public static boolean ovenIncomplete(List<ItemStack> grid) {
        return missing(ovenLines(grid));
    }

    private static boolean missing(List<Component> lines) {
        return !lines.isEmpty() && lines.getFirst().getContents() instanceof TranslatableContents t
                && t.getKey().equals("gui.fullcourse.pot.missing");
    }

    /** The pot arrow's lines for these grid items against these recipes, uncached. */
    public static List<Component> preview(List<ItemStack> grid, List<CookingRecipe> recipes) {
        return dishes(grid, recipes.stream().map(Dish::of).toList(), "gui.fullcourse.pot.unused");
    }

    private static List<Component> dishes(List<ItemStack> grid, List<Dish> recipes, String unused) {
        if (recipes.isEmpty() || grid.stream().allMatch(s -> s.isEmpty() || s.is(FoodTags.CONTAINERS))) return List.of();
        Dish nearest = null;
        int[] nearestMissing = null;
        for (Dish recipe : recipes) {
            List<Ingredient> ingredients = recipe.ingredients();
            int[] missing = IngredientMatch.missing(ingredients, grid, Ingredient::test, ItemStack::getCount,
                    ItemStack::isSameItemSameComponents, s -> s.is(FoodTags.CONTAINERS));
            if (missing == null || missing.length == ingredients.size()) continue;
            if (missing.length == 0) return List.of(Component.translatable("gui.fullcourse.pot.makes", dish(recipe, true)));
            if (nearestMissing == null || missing.length < nearestMissing.length
                    || missing.length == nearestMissing.length && stars(recipe) > stars(nearest)) {
                nearest = recipe;
                nearestMissing = missing;
            }
        }
        if (nearest == null) return List.of(strays(grid, recipes, unused));
        // one entry per ingredient still missing, counted ("Sugar ×2")
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        Map<String, Ingredient> ingredients = new LinkedHashMap<>();
        for (int i : nearestMissing) {
            Ingredient ingredient = nearest.ingredients().get(i);
            Component name = RecipeHints.whole(name(ingredient));
            String key = name.getString();
            counts.merge(key, 1, Integer::sum);
            names.putIfAbsent(key, name);
            ingredients.putIfAbsent(key, ingredient);
        }
        MutableComponent needs = Component.empty();
        boolean first = true;
        for (var e : counts.entrySet()) {
            if (!first) needs.append(Component.literal(", "));
            first = false;
            Component name = names.get(e.getKey());
            needs.append(e.getValue() > 1 ? Component.translatable("gui.fullcourse.pot.times", name, e.getValue()) : name);
        }
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("gui.fullcourse.pot.missing", dish(nearest, false), needs));
        // where an ingredient that's made first comes from ("Broth: Cooking Pot: Bone, in a Glass Bottle")
        for (var e : ingredients.entrySet()) {
            if (out.size() > MAX_FROM) break;
            var single = e.getValue().getSingleItem();
            if (single.isEmpty()) continue;
            List<Component> made = RecipeHints.lines(single.get().value());
            if (made.isEmpty()) continue;
            out.add(Component.translatable("gui.fullcourse.pot.from", names.get(e.getKey()), made.getFirst()).withStyle(ChatFormatting.DARK_AQUA));
        }
        return List.copyOf(out);
    }

    /** "Vegetable Stew ★★★, in a Bowl": the dish in its tier colour, its stars, and, if asked, what it's served in. */
    private static Component dish(Dish recipe, boolean servedIn) {
        ItemStack result = recipe.result();
        Tier tier = FoodProfiles.clientReady() ? FoodProfiles.client(result.getItem()).tier() : Tier.RAW;
        MutableComponent name = result.getHoverName().copy();
        if (tier.stars() > 0) name.append(Component.literal(" " + "★".repeat(tier.stars())));
        MutableComponent out = Component.empty().withColor(tier.color() & 0xFFFFFF).append(RecipeHints.whole(name));
        if (servedIn) recipe.container().ifPresent(c -> out.append(Component.translatable("hint.fullcourse.served_in", RecipeHints.whole(name(c)))));
        return out;
    }

    private static int stars(Dish recipe) {
        return FoodProfiles.clientReady() ? FoodProfiles.client(recipe.result().getItem()).tier().stars() : 0;
    }

    /** Names the first item no pot recipe uses; when each fits some recipe, says none uses them all. */
    private static Component strays(List<ItemStack> grid, List<Dish> recipes, String unused) {
        for (ItemStack stack : grid) {
            if (stack.isEmpty() || stack.is(FoodTags.CONTAINERS)) continue;
            boolean used = recipes.stream().anyMatch(r -> r.ingredients().stream().anyMatch(i -> i.test(stack)));
            if (!used) return Component.translatable(unused, RecipeHints.whole(stack.getHoverName())).withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("gui.fullcourse.pot.strays").withStyle(ChatFormatting.GRAY);
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
