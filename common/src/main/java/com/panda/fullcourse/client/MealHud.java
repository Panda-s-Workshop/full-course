package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.logic.Meals;
import com.panda.fullcourse.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The meal row where the food bar was: one 18×18 frame per slot, right-aligned to the hotbar's right edge,
 * the dish inside, its frame a ring in the tier's colour that drains clockwise from the top as the time runs out (the
 * icon itself stays clear); a lighter frame with a plate for free slots and dim padlocks for slots the journal hasn't
 * unlocked yet. Loaders place it and hide the food bar.
 */
public final class MealHud {
    public static final Identifier LAYER = FullCourse.id("meals");
    /** Height the row takes, including the gap above it, for the loaders' row stacking. */
    public static final int HEIGHT = 20;
    static final int FRAME = 18;
    /** A meal's ring blinks for its last tenth, but never less than this many ticks. */
    static final int ENDING_TICKS = 200;
    /** The ring's pixels, clockwise from the top middle. */
    private static final int[][] RING = ring();

    private MealHud() {}

    /** The server runs Full Course with hunger off: no food bar, so nothing that previews hunger or saturation is true. */
    public static boolean hungerReplaced() {
        return FoodProfiles.clientReady() && !FullCourseConfig.keepVanillaHunger();
    }

    /**
     * Whether the row replaces the food bar now: the server runs Full Course (it sent its table) with hunger removed, a
     * survival-like player, the HUD visible.
     */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null && hungerReplaced() && !mc.gui.hud.isHidden()
                && mc.gameMode != null && mc.gameMode.canHurtPlayer();
    }

    /**
     * Draws the row with its frames' bottom edge at {@code bottom} (the bottom of vanilla's food row, or of the mount's
     * hearts when riding). Returns whether it drew.
     */
    public static boolean extract(GuiGraphicsExtractor g, int bottom) {
        if (!active()) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        FoodState state = Services.PLATFORM.foodState(player);
        long now = player.level().getGameTime();
        int slots = state.slots();
        int max = FullCourseConfig.maxMealSlots();
        int right = g.guiWidth() / 2 + 91;
        int top = bottom - FRAME;
        for (int i = 0; i < max; i++) {
            int x = right - (i + 1) * FRAME - i;
            if (i >= slots) {
                lockedFrame(g, x, top);
                continue;
            }
            Meals.Meal<Identifier> meal = i < state.meals().active().size() ? state.meals().active().get(i) : null;
            if (meal == null) {
                freeFrame(g, x, top);
                continue;
            }
            long left = meal.remaining(now);
            long ending = Math.max(ENDING_TICKS, (meal.end() - meal.start()) / 10);
            boolean blinkOff = left < ending && (now / 5) % 2 == 0;
            frame(g, x, top, 0xFF262626, 0x88000000);
            int lit = Math.round(RING.length * meal.fraction(now));
            int color = blinkOff ? 0xFFFFFFFF : meal.tier().color();
            for (int p = RING.length - lit; p < RING.length; p++) g.fill(x + RING[p][0], top + RING[p][1], x + RING[p][0] + 1, top + RING[p][1] + 1, color);
            BuiltInRegistries.ITEM.get(meal.dish()).ifPresent(item -> g.item(new ItemStack(item.value()), x + 1, top + 1));
        }
        return true;
    }

    /** The frame of an 18×18 slot: a 1-pixel border in {@code border}, the inside in {@code inside}. */
    static void frame(GuiGraphicsExtractor g, int x, int y, int border, int inside) {
        g.fill(x + 1, y + 1, x + FRAME - 1, y + FRAME - 1, inside);
        g.fill(x, y, x + FRAME, y + 1, border);
        g.fill(x, y + FRAME - 1, x + FRAME, y + FRAME, border);
        g.fill(x, y + 1, x + 1, y + FRAME - 1, border);
        g.fill(x + FRAME - 1, y + 1, x + FRAME, y + FRAME - 1, border);
    }

    /** A free slot: a light frame with a faint plate in it. */
    static void freeFrame(GuiGraphicsExtractor g, int x, int y) {
        frame(g, x, y, 0xFF9A9A9A, 0x66000000);
        g.fill(x + 4, y + 9, x + 14, y + 11, 0x50FFFFFF);
        g.fill(x + 5, y + 8, x + 13, y + 12, 0x50FFFFFF);
    }

    /** A dim, see-through frame with a small padlock: a slot the Tasting Journal will unlock. */
    static void lockedFrame(GuiGraphicsExtractor g, int x, int y) {
        frame(g, x, y, 0x99303030, 0x33000000);
        int c = 0xAA6A6A6A;
        g.fill(x + 6, y + 5, x + 12, y + 6, c);      // shackle top
        g.fill(x + 6, y + 6, x + 7, y + 9, c);       // shackle sides
        g.fill(x + 11, y + 6, x + 12, y + 9, c);
        g.fill(x + 5, y + 9, x + 13, y + 14, c);     // body
        g.fill(x + 8, y + 10, x + 10, y + 12, 0xAA303030); // keyhole
    }

    /** The 68 border pixels of an 18×18 frame, clockwise from the top middle. */
    private static int[][] ring() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int m = FRAME - 1;
        for (int x = FRAME / 2; x <= m; x++) out.add(new int[] {x, 0});
        for (int y = 1; y <= m; y++) out.add(new int[] {m, y});
        for (int x = m - 1; x >= 0; x--) out.add(new int[] {x, m});
        for (int y = m - 1; y >= 0; y--) out.add(new int[] {0, y});
        for (int x = 1; x < FRAME / 2; x++) out.add(new int[] {x, 0});
        return out.toArray(new int[0][]);
    }

    /** Rows of mount hearts showing where vanilla draws them in place of the food bar (0 when not riding). */
    public static int vehicleHeartRows(LocalPlayer player) {
        if (!(player.getVehicle() instanceof LivingEntity mount) || !mount.showVehicleHealth()) return 0;
        int hearts = Math.min(30, (int) (mount.getMaxHealth() + 0.5F) / 2);
        return (hearts + 9) / 10;
    }
}
