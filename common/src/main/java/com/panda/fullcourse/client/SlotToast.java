package com.panda.fullcourse.client;

import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.registry.ModItems;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * A new meal slot, on the advancement toast's frame: the Tasting Journal, "A third meal slot" in the feast colour, and
 * what the next slot asks for, wrapped to the frame (the frame grows a line at a time). Shown as long as an
 * advancement toast.
 */
public final class SlotToast implements Toast {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
    private static final long SHOWN_MS = 5000;
    private static final int TEXT_X = 30, TEXT_WIDTH = 160 - TEXT_X - 5, LINE = 9;
    private final Component title;
    private final List<FormattedCharSequence> body;
    private Visibility visibility = Visibility.SHOW;

    public SlotToast(Component title, Component body) {
        this.title = title;
        this.body = Minecraft.getInstance().font.split(body, TEXT_WIDTH);
    }

    @Override
    public int height() {
        return 32 + LINE * Math.max(0, body.size() - 1);
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long shownMs) {
        visibility = shownMs >= SHOWN_MS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long shownMs) {
        // the frame's top and bottom halves, its plain middle row repeated between them
        int h = height();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 160, 32, 0, 0, 0, 0, 160, 16);
        for (int y = 16; y < h - 16; y++) g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 160, 32, 0, 16, 0, y, 160, 1);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 160, 32, 0, 16, 0, h - 16, 160, 16);
        g.item(new ItemStack(ModItems.item("tasting_journal")), 8, 8);
        g.text(font, title, TEXT_X, 7, Tier.FEAST.color(), false);
        for (int i = 0; i < body.size(); i++) g.text(font, body.get(i), TEXT_X, 18 + LINE * i, 0xFFFFFFFF, false);
    }
}
