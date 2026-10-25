package com.panda.fullcourse.compat;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * A machine recipe that names no placeable ingredients and shows no result (Create's mixers, mills and presses) is
 * still often a record holding both: its {@link Ingredient} fields and lists are read as the ingredients, and its first
 * {@link ItemStackTemplate} or {@link ItemStack} (or the first of a list of them) as the result. Anything else about it
 * (fluids, chances, heat) is ignored. Read reflectively, so nothing here needs the other mod.
 */
public final class RecipeFields {
    public record Read(List<Ingredient> ingredients, ItemStack result) {}

    private RecipeFields() {}

    public static Optional<Read> read(Recipe<?> recipe) {
        Class<?> type = recipe.getClass();
        if (!type.isRecord()) return Optional.empty();
        List<Ingredient> ingredients = new ArrayList<>();
        ItemStack result = ItemStack.EMPTY;
        try {
            for (RecordComponent component : type.getRecordComponents()) {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(recipe);
                if (value instanceof Ingredient ingredient) {
                    ingredients.add(ingredient);
                } else if (value instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Ingredient ingredient) ingredients.add(ingredient);
                        else if (result.isEmpty()) result = stack(o);
                    }
                } else if (result.isEmpty()) {
                    result = stack(value);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty(); // a record that won't be read from outside its mod
        }
        return ingredients.isEmpty() || result.isEmpty() ? Optional.empty() : Optional.of(new Read(List.copyOf(ingredients), result));
    }

    private static ItemStack stack(Object value) {
        if (value instanceof ItemStackTemplate template) return template.create();
        if (value instanceof ItemStack stack) return stack.copy();
        return ItemStack.EMPTY;
    }
}
