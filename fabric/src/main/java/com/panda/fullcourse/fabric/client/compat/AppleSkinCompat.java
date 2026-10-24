package com.panda.fullcourse.fabric.client.compat;

import com.panda.fullcourse.client.MealHud;
import squeek.appleskin.api.AppleSkinApi;
import squeek.appleskin.api.event.HUDOverlayEvent;
import squeek.appleskin.api.event.TooltipOverlayEvent;

/**
 * AppleSkin previews hunger, saturation and the health that saturation would bring back. With the food bar gone none
 * of that is true, so its HUD overlays and its food tooltip are hidden; our own tooltip says what food heals.
 * AppleSkin loads this through its {@code appleskin} entrypoint, so it never loads without AppleSkin.
 */
public final class AppleSkinCompat implements AppleSkinApi {
    @Override
    public void registerEvents() {
        HUDOverlayEvent.HungerRestored.EVENT.register(e -> e.isCanceled |= MealHud.hungerReplaced());
        HUDOverlayEvent.Saturation.EVENT.register(e -> e.isCanceled |= MealHud.hungerReplaced());
        HUDOverlayEvent.Exhaustion.EVENT.register(e -> e.isCanceled |= MealHud.hungerReplaced());
        HUDOverlayEvent.HealthRestored.EVENT.register(e -> e.isCanceled |= MealHud.hungerReplaced());
        TooltipOverlayEvent.Pre.EVENT.register(e -> e.isCanceled |= MealHud.hungerReplaced());
    }
}
