package com.panda.fullcourse.client;

import com.panda.fullcourse.registry.ModRecipes;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The kitchen's recipes on the client. Clients don't get the server's recipes on their own, so the server sends the
 * four kitchen types ({@link ModRecipes#kitchenTypes()}) through the loader's recipe sync, and the loader hands them
 * here. Recipe viewers read them.
 */
public final class KitchenRecipes {
    private static Map<RecipeType<?>, List<RecipeHolder<?>>> recipes = Map.of();

    private KitchenRecipes() {}

    /** The server's recipes arrived; {@code byType} gives each type's. */
    public static void received(Function<RecipeType<?>, Collection<? extends RecipeHolder<?>>> byType) {
        Map<RecipeType<?>, List<RecipeHolder<?>>> next = new HashMap<>();
        for (RecipeType<?> type : ModRecipes.kitchenTypes()) next.put(type, List.copyOf(byType.apply(type)));
        recipes = Map.copyOf(next);
    }

    /** The recipes of {@code type} the server sent, empty before they arrive. */
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> List<RecipeHolder<T>> of(RecipeType<T> type) {
        return (List<RecipeHolder<T>>) (List<?>) recipes.getOrDefault(type, List.of());
    }

    public static void clear() {
        recipes = Map.of();
    }
}
