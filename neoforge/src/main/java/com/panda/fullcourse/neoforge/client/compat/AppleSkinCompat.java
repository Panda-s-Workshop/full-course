package com.panda.fullcourse.neoforge.client.compat;

import com.panda.fullcourse.client.MealHud;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import squeek.appleskin.api.event.HUDOverlayEvent;
import squeek.appleskin.api.event.TooltipOverlayEvent;

/**
 * AppleSkin previews hunger, saturation and the health that saturation would bring back. With the food bar gone none
 * of that is true, so its HUD overlays and its food tooltip are hidden; our own tooltip says what food heals.
 * Registered only when AppleSkin is installed.
 */
public final class AppleSkinCompat {
    private AppleSkinCompat() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((HUDOverlayEvent.HungerRestored e) -> hide(e));
        NeoForge.EVENT_BUS.addListener((HUDOverlayEvent.Saturation e) -> hide(e));
        NeoForge.EVENT_BUS.addListener((HUDOverlayEvent.Exhaustion e) -> hide(e));
        NeoForge.EVENT_BUS.addListener((HUDOverlayEvent.HealthRestored e) -> hide(e));
        NeoForge.EVENT_BUS.addListener((TooltipOverlayEvent.Pre e) -> hide(e));
    }

    private static void hide(ICancellableEvent event) {
        if (MealHud.hungerReplaced()) event.setCanceled(true);
    }
}
