package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.kitchen.BrickOvenBlockEntity;
import com.panda.fullcourse.kitchen.BrickOvenMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Brick Oven's screen: vanilla's furnace flame burning down as the fuel does and its arrow for progress, with the
 * oven's status on the title row when something stops it. Hovering the flame says what it burns, the arrow how far
 * the dish is, the empties slot what lands there.
 */
public class BrickOvenScreen extends AbstractContainerScreen<BrickOvenMenu> {
    private static final Identifier TEXTURE = FullCourse.id("textures/gui/container/brick_oven.png");
    private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    public static final int FLAME_X = 68, FLAME_Y = 36, ARROW_X = 90, ARROW_Y = 35;
    private static final int STATUS_COLOR = 0xFFA0302A;

    public BrickOvenScreen(BrickOvenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.lit()) {
            // as the furnace: the flame shrinks from the top as the fuel burns
            int h = Mth.ceil(menu.fuelLeft() * 13F) + 1;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME, 14, 14, 0, 14 - h, leftPos + FLAME_X, topPos + FLAME_Y + 14 - h, 14, h);
        }
        int arrow = Mth.ceil(menu.progress() * 24F);
        if (arrow > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, leftPos + ARROW_X, topPos + ARROW_Y, arrow, 16);
    }

    /** The oven's status, right-aligned on the title row: red when something stops it. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        Component line = switch (menu.status()) {
            case NO_FUEL -> Component.translatable("gui.fullcourse.oven.status.no_fuel");
            case OUTPUT_FULL -> Component.translatable("gui.fullcourse.oven.status.full");
            case NO_RECIPE -> Component.translatable("gui.fullcourse.oven.status.no_recipe");
            default -> null;
        };
        if (line != null) g.text(font, line, imageWidth - 8 - font.width(line), titleLabelY, STATUS_COLOR, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.index == BrickOvenBlockEntity.EMPTIES && !hoveredSlot.hasItem()) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.fullcourse.oven.empties"), mouseX, mouseY);
        } else if (isHovering(FLAME_X, FLAME_Y, 14, 14, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, menu.lit() ? Component.translatable("gui.fullcourse.oven.lit").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("gui.fullcourse.oven.unlit"), mouseX, mouseY);
        } else if (isHovering(ARROW_X, ARROW_Y, 24, 16, mouseX, mouseY) && menu.progress() > 0) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.fullcourse.oven.progress", Math.round(menu.progress() * 100)), mouseX, mouseY);
        }
    }
}
