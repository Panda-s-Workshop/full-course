package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.network.ProfilesPayload;
import com.panda.fullcourse.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * The client's copy of the server's food table and config, and the first-join hint: a toast that says
 * hunger is gone and meals are the point, shown once per login until the player has eaten their first meal. Loaders
 * call {@link #received} with the server's table, {@link #tick} every client tick and {@link #disconnected} on leaving.
 */
public final class ClientSync {
    private static final SystemToast.SystemToastId HINT = new SystemToast.SystemToastId(10_000L);
    /** Ticks to wait after the table arrives, so the player's food state (synced separately) is there too. */
    private static final int HINT_DELAY = 60;
    private static int hintIn = -1;
    private static boolean hinted;

    private ClientSync() {}

    public static void received(ProfilesPayload payload) {
        payload.apply();
        if (!hinted) hintIn = HINT_DELAY;
    }

    public static void tick() {
        if (hintIn < 0 || --hintIn > 0) return;
        hintIn = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || hinted || FullCourseConfig.keepVanillaHunger()) return;
        hinted = true;
        if (!Services.PLATFORM.foodState(mc.player).tasted().isEmpty()) return;
        SystemToast.addOrUpdate(mc.gui.toastManager(), HINT, Component.translatable("toast.fullcourse.hint.title"),
                Component.translatable("toast.fullcourse.hint.body"));
    }

    /** The hint is on screen (for the harness). */
    public static boolean hintShowing() {
        return Minecraft.getInstance().gui.toastManager().getToast(SystemToast.class, HINT) != null;
    }

    public static void disconnected() {
        FoodProfiles.clearClient();
        FullCourseConfig.clearServer();
        hinted = false;
        hintIn = -1;
    }
}
