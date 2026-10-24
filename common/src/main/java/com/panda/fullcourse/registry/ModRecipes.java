package com.panda.fullcourse.registry;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.kitchen.BakingRecipe;
import com.panda.fullcourse.kitchen.CookingRecipe;
import com.panda.fullcourse.kitchen.CuttingRecipe;
import com.panda.fullcourse.kitchen.FermentingRecipe;
import com.panda.fullcourse.platform.registry.DeferredHolder;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The kitchen's recipe types: {@code fullcourse:cutting} (board), {@code fullcourse:cooking} (pot), {@code fullcourse:baking}
 * (oven) and {@code fullcourse:fermenting} (crock).
 */
public final class ModRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FullCourse.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FullCourse.MODID);
    private static final DeferredRegister<RecipeBookCategory> CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, FullCourse.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<CuttingRecipe>> CUTTING = TYPES.register("cutting", () -> type("cutting"));
    public static final DeferredHolder<RecipeType<?>, RecipeType<CookingRecipe>> COOKING = TYPES.register("cooking", () -> type("cooking"));
    public static final DeferredHolder<RecipeType<?>, RecipeType<BakingRecipe>> BAKING = TYPES.register("baking", () -> type("baking"));
    public static final DeferredHolder<RecipeType<?>, RecipeType<FermentingRecipe>> FERMENTING = TYPES.register("fermenting", () -> type("fermenting"));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CuttingRecipe>> CUTTING_SERIALIZER = SERIALIZERS.register("cutting",
            () -> new RecipeSerializer<>(CuttingRecipe.MAP_CODEC, CuttingRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CookingRecipe>> COOKING_SERIALIZER = SERIALIZERS.register("cooking",
            () -> new RecipeSerializer<>(CookingRecipe.MAP_CODEC, CookingRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BakingRecipe>> BAKING_SERIALIZER = SERIALIZERS.register("baking",
            () -> new RecipeSerializer<>(BakingRecipe.MAP_CODEC, BakingRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FermentingRecipe>> FERMENTING_SERIALIZER = SERIALIZERS.register("fermenting",
            () -> new RecipeSerializer<>(FermentingRecipe.MAP_CODEC, FermentingRecipe.STREAM_CODEC));

    /** No screen shows these: they exist because every recipe names a category. */
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> CUTTING_CATEGORY = CATEGORIES.register("cutting", RecipeBookCategory::new);
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> COOKING_CATEGORY = CATEGORIES.register("cooking", RecipeBookCategory::new);
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> BAKING_CATEGORY = CATEGORIES.register("baking", RecipeBookCategory::new);
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> FERMENTING_CATEGORY = CATEGORIES.register("fermenting", RecipeBookCategory::new);

    private ModRecipes() {}

    /** The four kitchen recipe types, which the server sends to clients for recipe viewers. */
    public static List<RecipeType<?>> kitchenTypes() {
        return List.of(CUTTING.get(), COOKING.get(), BAKING.get(), FERMENTING.get());
    }

    /** Their serializers, for loaders that sync recipes by serializer. */
    public static List<RecipeSerializer<?>> kitchenSerializers() {
        return List.of(CUTTING_SERIALIZER.get(), COOKING_SERIALIZER.get(), BAKING_SERIALIZER.get(), FERMENTING_SERIALIZER.get());
    }

    private static <T extends Recipe<?>> RecipeType<T> type(String name) {
        String id = FullCourse.id(name).toString();
        return new RecipeType<>() {
            @Override
            public String toString() {
                return id;
            }
        };
    }
}
