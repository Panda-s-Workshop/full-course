package com.panda.fullcourse.client;

import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.registry.ModItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * A new meal slot, on the advancement toast's frame: the Tasting Journal, "A third meal slot" in the feast colour, and
 * what the next slot asks for. Shown as long as an advancement toast.
 */
public final class SlotToast implements Toast {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
    private static final long SHOWN_MS = 5000;
    private final Component title;
    private final Component body;
    private Visibility visibility = Visibility.SHOW;

    public SlotToast(Component title, Component body) {
        this.title = title;
        this.body = body;
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
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
        g.item(new ItemStack(ModItems.item("tasting_journal")), 8, 8);
        g.text(font, title, 30, 7, Tier.FEAST.color(), false);
        g.text(font, body, 30, 18, 0xFFFFFFFF, false);
    }
}
