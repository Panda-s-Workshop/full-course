package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.food.Tasting;
import com.panda.fullcourse.logic.Milestones;
import com.panda.fullcourse.network.ProfilesPayload;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The client's copy of the server's food table and config, and the first-join hint: a toast that says
 * hunger is gone and meals are the point, shown once per login until the player has eaten their first meal; and the
 * toast and chime when the Tasting Journal's milestones unlock a meal slot; and a heart on each Recovery heal when the
 * player's {@link ClientOptions} ask for it. Loaders
 * call {@link #received} with the server's table, {@link #tick} every client tick and {@link #disconnected} on leaving.
 */
public final class ClientSync {
    private static final SystemToast.SystemToastId HINT = new SystemToast.SystemToastId(10_000L);
    /** Ticks to wait after the table arrives, so the player's food state (synced separately) is there too. */
    private static final int HINT_DELAY = 60;
    private static int hintIn = -1;
    private static boolean hinted;
    /** Unlocked slots last seen, once the login's state has arrived; -1 until then, so joining doesn't announce. */
    private static int slotsSeen = -1;

    private ClientSync() {}

    public static void received(ProfilesPayload payload) {
        payload.apply();
        if (!hinted) hintIn = HINT_DELAY;
    }

    public static void tick() {
        slotToast();
        if (hintIn < 0 || --hintIn > 0) return;
        hintIn = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        slotsSeen = Services.PLATFORM.foodState(mc.player).slots();
        if (hinted || FullCourseConfig.keepVanillaHunger()) return;
        hinted = true;
        if (!Services.PLATFORM.foodState(mc.player).tasted().isEmpty()) return;
        SystemToast.addOrUpdate(mc.gui.toastManager(), HINT, Component.translatable("toast.fullcourse.hint.title"),
                Component.translatable("toast.fullcourse.hint.body"));
    }

    /** A slot just unlocked: a toast naming it and what the next one asks for, and a chime. */
    private static void slotToast() {
        Minecraft mc = Minecraft.getInstance();
        if (slotsSeen < 0 || mc.player == null) return;
        FoodState state = Services.PLATFORM.foodState(mc.player);
        int slots = state.slots();
        // only ever up: a respawn or a dimension change may show an empty state for a tick, and that's no new slot
        if (slots <= slotsSeen) return;
        slotsSeen = slots;
        String ordinal = "gui.fullcourse.ordinal." + slots;
        Component title = Language.getInstance().has(ordinal)
                ? Component.translatable("toast.fullcourse.slot.title", Component.translatable(ordinal))
                : Component.translatable("toast.fullcourse.slot.title_n", slots);
        Milestones.Next next = Milestones.next(Tasting.clientCount(state.tasted()), state.bonusSlots(), FullCourseConfig.slotMilestones());
        Component body = next == Milestones.Next.NONE || slots >= FullCourseConfig.maxMealSlots()
                ? Component.translatable("toast.fullcourse.slot.last")
                : Component.translatable(next.needsFeast() ? "toast.fullcourse.slot.next_feast" : "toast.fullcourse.slot.next",
                        next.need(), "★".repeat(next.stars()));
        mc.gui.toastManager().addToast(new SlotToast(title, body));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SLOT_UNLOCK.get(), 1F, 1F));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SLOT_UNLOCK_FANFARE.get(), 1F, 1F));
    }

    /** Recovery heals the client was told of, and hearts it showed for them (for checks). */
    private static int recoveries, hearts;

    /** Recovery healed this player: a heart where they can see it, if they asked for it. */
    public static void recovered() {
        recoveries++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!ClientOptions.showRecoveryParticles() || player == null || mc.level == null) return;
        hearts++;
        Vec3 at;
        if (mc.options.getCameraType().isFirstPerson()) {
            // in front of the eyes and a little low, rising into view
            Vec3 look = player.getLookAngle();
            at = player.getEyePosition().add(look.x * 0.9, look.y * 0.9 - 0.45, look.z * 0.9);
        } else {
            at = new Vec3(player.getX(), player.getY() + player.getBbHeight() + 0.3, player.getZ());
        }
        mc.level.addParticle(ParticleTypes.HEART, at.x, at.y, at.z, 0, 0.02, 0);
    }

    public static int recoveriesHeard() {
        return recoveries;
    }

    public static int heartsShown() {
        return hearts;
    }

    /** The slot toast is on screen (for checks). */
    public static boolean slotToastShowing() {
        return Minecraft.getInstance().gui.toastManager().getToast(SlotToast.class, Toast.NO_TOKEN) != null;
    }

    /** The hint is on screen (for checks). */
    public static boolean hintShowing() {
        return Minecraft.getInstance().gui.toastManager().getToast(SystemToast.class, HINT) != null;
    }

    public static void disconnected() {
        FoodProfiles.clearClient();
        KitchenRecipes.clear();
        FullCourseConfig.clearServer();
        hinted = false;
        hintIn = -1;
        slotsSeen = -1;
    }
}
