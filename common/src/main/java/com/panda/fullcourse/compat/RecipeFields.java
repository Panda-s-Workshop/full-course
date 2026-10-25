package com.panda.fullcourse.compat;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * A machine recipe that names no placeable ingredients and shows no result (Create's mixers, mills and presses) is
 * still often a record holding both. Its {@link Ingredient} fields and lists are read as the ingredients, along with
 * anything in them that hands one over through {@code getIngredient()} or {@code ingredient()} (Create's sized
 * ingredients). Its result is the first {@link ItemStackTemplate} or {@link ItemStack}, or anything that makes one through
 * {@code create()} (Create's processing outputs), preferring a component named {@code result} or {@code results} (a
 * sequenced assembly's transitional item isn't its result). A component named for fluids, or holding them, marks a
 * recipe that takes fluids (Create's chocolate bar, pressed from melted chocolate alone). Anything else about it
 * (chances, heat) is ignored. Read reflectively, so nothing here needs the other mod.
 */
public final class RecipeFields {
    public record Read(List<Ingredient> ingredients, ItemStack result, boolean fluids) {
        /** A result, but nothing it's made from that can be read: no ingredient and no fluid. */
        public boolean unread() {
            return ingredients.isEmpty() && !fluids;
        }
    }

    private RecipeFields() {}

    public static Optional<Read> read(Recipe<?> recipe) {
        return fields(recipe);
    }

    /** {@link #read} for any record, recipe or not. */
    public static Optional<Read> fields(Object recipe) {
        Class<?> type = recipe.getClass();
        if (!type.isRecord()) return Optional.empty();
        List<Ingredient> ingredients = new ArrayList<>();
        ItemStack named = ItemStack.EMPTY;
        ItemStack other = ItemStack.EMPTY;
        boolean fluids = false;
        try {
            for (RecordComponent component : type.getRecordComponents()) {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(recipe);
                boolean result = component.getName().startsWith("result");
                List<?> values = value instanceof List<?> list ? list : Collections.singletonList(value);
                // a fluid made isn't one taken
                if (!result && component.getName().toLowerCase(java.util.Locale.ROOT).contains("fluid") && value != null && !values.isEmpty()) fluids = true;
                for (Object o : values) {
                    if (o != null && o.getClass().getSimpleName().contains("Fluid")) {
                        fluids |= !result;
                        continue;
                    }
                    Ingredient ingredient = ingredient(o);
                    if (ingredient != null) {
                        ingredients.add(ingredient);
                        continue;
                    }
                    ItemStack stack = stack(o);
                    if (stack.isEmpty()) continue;
                    if (result && named.isEmpty()) named = stack;
                    else if (other.isEmpty()) other = stack;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty(); // a record that won't be read from outside its mod
        }
        ItemStack result = named.isEmpty() ? other : named;
        return result.isEmpty() ? Optional.empty() : Optional.of(new Read(List.copyOf(ingredients), result, fluids));
    }

    private static Ingredient ingredient(Object value) {
        if (value instanceof Ingredient ingredient) return ingredient;
        return call(value, Ingredient.class, "getIngredient", "ingredient") instanceof Ingredient ingredient ? ingredient : null;
    }

    private static ItemStack stack(Object value) {
        if (value instanceof ItemStackTemplate template) return template.create();
        if (value instanceof ItemStack stack) return stack.copy();
        return call(value, ItemStack.class, "create") instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
    }

    /** What the first of {@code names} that {@code value} answers with no arguments and a {@code returns} gives, or null. */
    private static Object call(Object value, Class<?> returns, String... names) {
        if (value == null) return null;
        for (String name : names) {
            try {
                Method method = value.getClass().getMethod(name);
                if (!returns.isAssignableFrom(method.getReturnType())) continue;
                method.setAccessible(true);
                return method.invoke(value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // not this one
            }
        }
        return null;
    }
}
