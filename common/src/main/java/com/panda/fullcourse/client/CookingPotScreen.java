package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.kitchen.CookingPotMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Cooking Pot's screen: vanilla's furnace arrow for progress and its flame for heat. A line beside the title says why the pot isn't cooking (no heat, a missing bowl, a full output, no recipe).
 * Hovering the flame says where heat comes from; hovering the arrow says how far the dish is.
 */
public class CookingPotScreen extends AbstractContainerScreen<CookingPotMenu> {
    private static final Identifier TEXTURE = FullCourse.id("textures/gui/container/cooking_pot.png");
    private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    /** Vanilla's dark red for text on a container panel. */
    private static final int STATUS_COLOR = 0xFFA0302A;
    public static final int FLAME_X = 49, FLAME_Y = 56, ARROW_X = 89, ARROW_Y = 26;

    public CookingPotScreen(CookingPotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.heated()) g.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME, 14, 14, 0, 0, leftPos + FLAME_X, topPos + FLAME_Y, 14, 14);
        int arrow = Mth.ceil(menu.progress() * 24F);
        if (arrow > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, leftPos + ARROW_X, topPos + ARROW_Y, arrow, 16);
    }

    /** The pot's status, right-aligned on the title row: red when something stops it. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        Component line = switch (menu.status()) {
            case NO_HEAT -> Component.translatable("gui.fullcourse.pot.status.no_heat");
            case NEEDS_CONTAINER -> Component.translatable("gui.fullcourse.pot.status.needs", menu.needs().getHoverName());
            case OUTPUT_FULL -> Component.translatable("gui.fullcourse.pot.status.full");
            case NO_RECIPE -> Component.translatable("gui.fullcourse.pot.status.no_recipe");
            default -> null;
        };
        if (line != null) g.text(font, line, imageWidth - 8 - font.width(line), titleLabelY, STATUS_COLOR, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (isHovering(FLAME_X, FLAME_Y, 14, 14, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, menu.heated() ? Component.translatable("gui.fullcourse.pot.heated").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("gui.fullcourse.pot.cold"), mouseX, mouseY);
        } else if (isHovering(ARROW_X, ARROW_Y, 24, 16, mouseX, mouseY) && menu.progress() > 0) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.fullcourse.pot.progress", Math.round(menu.progress() * 100)), mouseX, mouseY);
        }
    }
}
