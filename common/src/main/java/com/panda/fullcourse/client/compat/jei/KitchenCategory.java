package com.panda.fullcourse.client.compat.jei;

import com.panda.fullcourse.client.HeatText;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.food.Profile;
import com.panda.fullcourse.kitchen.BakingRecipe;
import com.panda.fullcourse.kitchen.CookingRecipe;
import com.panda.fullcourse.kitchen.CuttingRecipe;
import com.panda.fullcourse.kitchen.FermentingRecipe;
import com.panda.fullcourse.kitchen.KitchenTags;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.ITextWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;

/**
 * One kitchen station's page in JEI: its name and block as the title and icon, the ingredients in a grid, an arrow,
 * the result with its stars when it gives a meal, and what the station needs (a knife, heat below, fuel, time).
 */
abstract class KitchenCategory<R extends Recipe<?>> implements IRecipeCategory<RecipeHolder<R>> {
    private final IRecipeHolderType<R> type;
    private final Component title;
    private final IDrawable icon;
    private final int width, height;

    KitchenCategory(IRecipeHolderType<R> type, String station, ItemLike block, IGuiHelper gui, int width, int height) {
        this.type = type;
        this.title = Component.translatable("station.fullcourse." + station);
        this.icon = gui.createDrawableItemLike(block);
        this.width = width;
        this.height = height;
    }

    @Override
    public IRecipeHolderType<R> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    /** {@code ingredients} in a grid {@code columns} wide from (x, y), 18 px apart. */
    static void grid(IRecipeLayoutBuilder layout, List<Ingredient> ingredients, int columns, int x, int y) {
        for (int i = 0; i < ingredients.size(); i++) {
            layout.addInputSlot(x + (i % columns) * 18, y + (i / columns) * 18).setStandardSlotBackground().add(ingredients.get(i));
        }
    }

    /** "0:10" under the arrow: how long the station takes. */
    static ITextWidget time(IRecipeExtrasBuilder extras, int ticks, int x, int y) {
        return extras.addText(Component.literal(FoodText.duration(ticks)), 30, 10).setPosition(x, y).setColor(0xFF606060);
    }

    private static final int STARS_W = 50;
    /** A widget tooltip's width, in lines no wider than this. */
    private static final int TOOLTIP_WIDTH = 160;

    /**
     * The result's stars in its tier's colour, centred under the output slot whose item sits at x, from y down, if it
     * gives a meal. The box is wide enough for five stars, so JEI never cuts them short.
     */
    static void stars(IRecipeExtrasBuilder extras, ItemStackTemplate result, int x, int y) {
        Profile profile = FoodProfiles.client(result.create().getItem());
        if (!profile.givesMeal()) return;
        extras.addText(Component.literal("★".repeat(profile.tier().stars())), STARS_W, 10)
                .setPosition(x + 8 - STARS_W / 2, y, STARS_W, 10, HorizontalAlignment.CENTER, VerticalAlignment.TOP)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setColor(profile.tier().color());
    }

    /** Board: the item and a knife, cut into the result. */
    static final class Cutting extends KitchenCategory<CuttingRecipe> {
        Cutting(IRecipeHolderType<CuttingRecipe> type, ItemLike block, IGuiHelper gui) {
            super(type, "board", block, gui, 96, 22);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder layout, RecipeHolder<CuttingRecipe> holder, IFocusGroup focuses) {
            layout.addInputSlot(1, 3).setStandardSlotBackground().add(holder.value().input());
            layout.addSlot(RecipeIngredientRole.CRAFTING_STATION, 21, 3).setStandardSlotBackground().add(Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(KitchenTags.KNIVES)));
            layout.addOutputSlot(75, 3).setOutputSlotBackground().add(holder.value().output());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, RecipeHolder<CuttingRecipe> holder, IFocusGroup focuses) {
            extras.addRecipeArrowWidget().setPosition(44, 3);
        }
    }

    /** Pot: up to six ingredients over heat, served in its container. */
    static final class Cooking extends KitchenCategory<CookingRecipe> {
        Cooking(IRecipeHolderType<CookingRecipe> type, ItemLike block, IGuiHelper gui) {
            super(type, "pot", block, gui, 120, 56);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder layout, RecipeHolder<CookingRecipe> holder, IFocusGroup focuses) {
            CookingRecipe r = holder.value();
            grid(layout, r.ingredients(), 3, 1, 1);
            r.container().ifPresent(c -> layout.addInputSlot(64, 37).setStandardSlotBackground().add(c)
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable("gui.fullcourse.jei.served_in").withStyle(ChatFormatting.GRAY))));
            layout.addOutputSlot(99, 10).setOutputSlotBackground().add(r.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, RecipeHolder<CookingRecipe> holder, IFocusGroup focuses) {
            extras.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(64, 10);
            extras.addAnimatedRecipeFlameWidget(300).setPosition(20, 40).setTooltip(HeatText.needsHeat());
            time(extras, holder.value().time(), 84, 42);
            stars(extras, holder.value().result(), 99, 32);
        }
    }

    /** Oven: up to four ingredients baked with fuel. */
    static final class Baking extends KitchenCategory<BakingRecipe> {
        Baking(IRecipeHolderType<BakingRecipe> type, ItemLike block, IGuiHelper gui) {
            super(type, "oven", block, gui, 100, 42);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder layout, RecipeHolder<BakingRecipe> holder, IFocusGroup focuses) {
            grid(layout, holder.value().ingredients(), 2, 1, 1);
            layout.addOutputSlot(79, 10).setOutputSlotBackground().add(holder.value().result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, RecipeHolder<BakingRecipe> holder, IFocusGroup focuses) {
            extras.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(44, 10);
            extras.addAnimatedRecipeFlameWidget(300).setPosition(48, -2).setTooltip(Component.translatable("gui.fullcourse.oven.unlit"));
            time(extras, holder.value().time(), 44, 28);
            stars(extras, holder.value().result(), 79, 32);
        }
    }

    /** Crock: up to four ingredients sealed for a while. */
    static final class Fermenting extends KitchenCategory<FermentingRecipe> {
        Fermenting(IRecipeHolderType<FermentingRecipe> type, ItemLike block, IGuiHelper gui) {
            super(type, "crock", block, gui, 100, 42);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder layout, RecipeHolder<FermentingRecipe> holder, IFocusGroup focuses) {
            grid(layout, holder.value().ingredients(), 2, 1, 1);
            layout.addOutputSlot(79, 10).setOutputSlotBackground().add(holder.value().result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, RecipeHolder<FermentingRecipe> holder, IFocusGroup focuses) {
            extras.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(44, 10);
            // the crock goes by the overworld's clock too, so sleeping through the night counts toward this; in lines,
            // as JEI draws a widget's tooltip as given
            time(extras, holder.value().time(), 44, 28).setTooltip(net.minecraft.client.Minecraft.getInstance().font.getSplitter()
                    .splitLines(Component.translatable("gui.fullcourse.jei.crock_sleep"), TOOLTIP_WIDTH, net.minecraft.network.chat.Style.EMPTY));
            stars(extras, holder.value().result(), 79, 32);
        }
    }
}
