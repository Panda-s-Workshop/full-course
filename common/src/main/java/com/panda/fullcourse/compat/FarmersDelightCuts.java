package com.panda.fullcourse.compat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Farmer's Delight's cutting board recipes say they can't be placed and show no result, so the recipe walk would skip
 * them and every cut would count as a raw ingredient. Their input and first result are read through the recipe's own
 * {@code getInput()} and {@code getResults()} instead; the knife is a tool, not an ingredient. Read reflectively, so
 * nothing here needs Farmer's Delight to be installed.
 */
public final class FarmersDelightCuts {
    public static final Identifier CUTTING = Identifier.fromNamespaceAndPath("farmersdelight", "cutting");

    public record Cut(Ingredient input, ItemStack result) {}

    private FarmersDelightCuts() {}

    public static Optional<Cut> read(Recipe<?> recipe) {
        if (!CUTTING.equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()))) return Optional.empty();
        try {
            Method input = recipe.getClass().getMethod("getInput");
            Method results = recipe.getClass().getMethod("getResults");
            if (!(input.invoke(recipe) instanceof Ingredient ingredient)) return Optional.empty();
            if (!(results.invoke(recipe) instanceof List<?> list) || list.isEmpty()) return Optional.empty();
            if (!(list.getFirst() instanceof ItemStackTemplate first)) return Optional.empty();
            return Optional.of(new Cut(ingredient, first.create()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty(); // another version's shape: the cut stays a raw ingredient
        } catch (LinkageError e) {
            LinkageWarnings.once(recipe.getClass(), "a cutting recipe", e);
            return Optional.empty();
        }
    }
}
