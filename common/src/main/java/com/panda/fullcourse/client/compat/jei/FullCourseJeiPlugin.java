package com.panda.fullcourse.client.compat.jei;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.client.BrickOvenScreen;
import com.panda.fullcourse.client.CookingPotScreen;
import com.panda.fullcourse.client.HeatText;
import com.panda.fullcourse.client.JournalScreen;
import com.panda.fullcourse.client.KitchenRecipes;
import com.panda.fullcourse.client.MealList;
import com.panda.fullcourse.kitchen.BakingRecipe;
import com.panda.fullcourse.kitchen.CookingRecipe;
import com.panda.fullcourse.kitchen.CuttingRecipe;
import com.panda.fullcourse.kitchen.FermentingRecipe;
import com.panda.fullcourse.kitchen.KitchenTags;
import com.panda.fullcourse.registry.ModBlocks;
import com.panda.fullcourse.registry.ModItems;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * The kitchen in JEI: a page per station (Cutting Board, Cooking Pot, Brick Oven, Fermenting Crock) with its recipes,
 * the stations and knives as the blocks that make them, information pages for the stations, knives, feasts and the
 * journal, the journal's tiles for JEI's recipe and use keys, and the pot's and oven's arrows open their pages (the pot's
 * tooltip keeps the arrow's preview). JEI keeps its panels out of the meal list beside the inventory. JEI finds
 * this through its annotation on NeoForge and the {@code jei_mod_plugin} entrypoint on Fabric; nothing loads it
 * without JEI.
 */
@JeiPlugin
public final class FullCourseJeiPlugin implements IModPlugin {
    private static final Supplier<IRecipeHolderType<CuttingRecipe>> CUTTING = IRecipeHolderType.createDeferred(ModRecipes.CUTTING);
    private static final Supplier<IRecipeHolderType<CookingRecipe>> COOKING = IRecipeHolderType.createDeferred(ModRecipes.COOKING);
    private static final Supplier<IRecipeHolderType<BakingRecipe>> BAKING = IRecipeHolderType.createDeferred(ModRecipes.BAKING);
    private static final Supplier<IRecipeHolderType<FermentingRecipe>> FERMENTING = IRecipeHolderType.createDeferred(ModRecipes.FERMENTING);

    @Override
    public Identifier getPluginUid() {
        return FullCourse.id("kitchen");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new KitchenCategory.Cutting(CUTTING.get(), ModBlocks.CUTTING_BOARD.get(), gui),
                new KitchenCategory.Cooking(COOKING.get(), ModBlocks.COOKING_POT.get(), gui),
                new KitchenCategory.Baking(BAKING.get(), ModBlocks.BRICK_OVEN.get(), gui),
                new KitchenCategory.Fermenting(FERMENTING.get(), ModBlocks.FERMENTING_CROCK.get(), gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CUTTING.get(), KitchenRecipes.of(ModRecipes.CUTTING.get()));
        registration.addRecipes(COOKING.get(), KitchenRecipes.of(ModRecipes.COOKING.get()));
        registration.addRecipes(BAKING.get(), KitchenRecipes.of(ModRecipes.BAKING.get()));
        registration.addRecipes(FERMENTING.get(), KitchenRecipes.of(ModRecipes.FERMENTING.get()));
        // an information page for each thing whose use isn't a recipe
        registration.addIngredientInfo(ModBlocks.CUTTING_BOARD.get(), Component.translatable("jei.fullcourse.info.cutting_board"));
        BuiltInRegistries.ITEM.getTagOrEmpty(KitchenTags.KNIVES).forEach(knife ->
                registration.addIngredientInfo(knife.value(), Component.translatable("jei.fullcourse.info.knife")));
        List<Component> pot = new ArrayList<>(List.of(Component.translatable("jei.fullcourse.info.cooking_pot")));
        pot.addAll(HeatText.needsHeatOnPage());
        registration.addIngredientInfo(ModBlocks.COOKING_POT.get(), pot.toArray(Component[]::new));
        registration.addIngredientInfo(ModBlocks.BRICK_OVEN.get(), Component.translatable("jei.fullcourse.info.brick_oven"));
        registration.addIngredientInfo(ModBlocks.FERMENTING_CROCK.get(), Component.translatable("jei.fullcourse.info.fermenting_crock"));
        for (ItemLike feast : List.of(ModBlocks.ROAST_BANQUET.get(), ModBlocks.HARVEST_PIE.get(), ModBlocks.GLOW_BERRY_TART.get())) {
            registration.addIngredientInfo(feast, Component.translatable("jei.fullcourse.info.feast"));
        }
        registration.addIngredientInfo(ModItems.item("tasting_journal"), Component.translatable("jei.fullcourse.info.tasting_journal"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CUTTING.get(), ModBlocks.CUTTING_BOARD.get());
        BuiltInRegistries.ITEM.getTagOrEmpty(KitchenTags.KNIVES).forEach(knife -> registration.addCraftingStation(CUTTING.get(), knife.value()));
        registration.addCraftingStation(COOKING.get(), ModBlocks.COOKING_POT.get());
        registration.addCraftingStation(BAKING.get(), ModBlocks.BRICK_OVEN.get());
        registration.addCraftingStation(FERMENTING.get(), ModBlocks.FERMENTING_CROCK.get());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // the pot's arrow opens the pot page; the screen's own tooltip, placed below the arrow, says so
        registration.addGuiContainerHandler(CookingPotScreen.class, new IGuiContainerHandler<CookingPotScreen>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(CookingPotScreen screen, double mouseX, double mouseY) {
                IGuiClickableArea basic = IGuiClickableArea.createBasic(CookingPotScreen.ARROW_X, CookingPotScreen.ARROW_Y, 24, 16, COOKING.get());
                return List.of(new IGuiClickableArea() {
                    @Override
                    public Rect2i getArea() {
                        return basic.getArea();
                    }

                    @Override
                    public boolean isTooltipEnabled() {
                        return false;
                    }

                    @Override
                    public void onClick(IFocusFactory focusFactory, IRecipesGui recipesGui) {
                        basic.onClick(focusFactory, recipesGui);
                    }
                });
            }
        });
        CookingPotScreen.arrowOpensViewer = true;
        // JEI's panels stay out of the meal list beside the inventory
        registration.addGuiContainerHandler(InventoryScreen.class, new IGuiContainerHandler<InventoryScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
                Rect2i area = MealList.area();
                return area == null ? List.of() : List.of(area);
            }
        });
        registration.addRecipeClickArea(BrickOvenScreen.class, BrickOvenScreen.ARROW_X, BrickOvenScreen.ARROW_Y, 24, 16, BAKING.get());
        // the journal's tiles are dishes to JEI, so its keys for recipes, uses and bookmarks work over them
        registration.addGuiScreenHandler(JournalScreen.class, new IScreenHandler<JournalScreen>() {
            @Override
            public IGuiProperties apply(JournalScreen screen) {
                return new IGuiProperties() {
                    @Override public Class<? extends Screen> screenClass() { return JournalScreen.class; }
                    @Override public int guiLeft() { return screen.left(); }
                    @Override public int guiTop() { return screen.top(); }
                    @Override public int guiXSize() { return JournalScreen.W; }
                    @Override public int guiYSize() { return JournalScreen.H; }
                    @Override public int screenWidth() { return screen.width; }
                    @Override public int screenHeight() { return screen.height; }
                };
            }

            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory factory, JournalScreen screen,
                                                                                               double mouseX, double mouseY) {
                return screen.tileAt(mouseX, mouseY).flatMap(tile -> factory.createBuilder(new ItemStack(tile.item())).buildWithArea(tile.x(), tile.y(), 16, 16));
            }
        });
    }
}
