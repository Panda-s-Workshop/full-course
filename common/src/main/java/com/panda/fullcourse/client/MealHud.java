package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.logic.Meals;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.platform.Services;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The meal row where the food bar was: one 18×18 frame per slot, right-aligned to the hotbar's right edge,
 * the dish inside, its frame a ring in the tier's colour that drains clockwise from the top as the time runs out, over
 * a dim track of the same colour so a meal about to end still shows its tier (the icon itself stays clear); a lighter frame with a plate for free slots and dim padlocks for slots the journal hasn't
 * unlocked yet. Holding a food that gives a meal marks the slot it would take with a small arrow above it: white over
 * the free slot it fills (shown there, dimmed) or its own slot to refresh, red over the meal it would replace (dimmed).
 * Five frames fit between the hearts and the hotbar's right edge; a server allowing more slots gets more rows, stacked
 * upward. Loaders place it and hide the food bar; the player's {@code mealRowOffset} lifts it, and the loaders lift what
 * stacks above it by {@link #lift}.
 */
public final class MealHud {
    public static final Identifier LAYER = FullCourse.id("meals");
    /** Height a row takes, including the gap above it, for the loaders' row stacking. */
    public static final int HEIGHT = 20;
    static final int FRAME = 18;
    /** The held-food marker's height above the top row ({@link #marker}): what stacks above keeps clear of it. */
    public static final int MARKER_ROOM = 4;
    /** From one row's top to the next row's: a frame and room for the marker over the lower row's slots. */
    public static final int ROW_STEP = FRAME + MARKER_ROOM;
    /** Frames per row: a sixth would reach over the hearts. */
    static final int PER_ROW = 5;
    /** The top of the frames as last drawn (for checks). */
    private static int lastTop;

    public static int lastTop() {
        return lastTop;
    }

    /** What the rows add above the food row's place: their height plus the player's {@code mealRowOffset}. */
    public static int lift() {
        return HEIGHT + ROW_STEP * (rows() - 1) + ClientOptions.mealRowOffset();
    }

    /** Rows of frames for the server's most slots. */
    public static int rows() {
        return (FullCourseConfig.maxMealSlots() + PER_ROW - 1) / PER_ROW;
    }
    /** A meal's ring blinks for its last tenth, but never less than this many ticks. */
    static final int ENDING_TICKS = 200;
    /** The whole ring's colour on a blink's off beat: the drained track too, so a nearly spent meal still flashes. */
    static final int BLINK_COLOR = 0xFFFFFFFF;
    /** The marker over a meal the held food would replace. */
    static final int REPLACE_COLOR = 0xFFE8383A;
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
        bottom -= ClientOptions.mealRowOffset();
        lastTop = bottom - FRAME - (rows() - 1) * ROW_STEP;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        FoodState state = Services.PLATFORM.foodState(player);
        long now = player.level().getGameTime();
        int slots = state.slots();
        int max = FullCourseConfig.maxMealSlots();
        int right = g.guiWidth() / 2 + 91;
        int firstTop = bottom - FRAME;
        ItemStack held = heldMeal(player);
        Meals.Target target = preview(player, state);
        for (int i = 0; i < max; i++) {
            int col = i % PER_ROW;
            int x = right - (col + 1) * FRAME - col, top = firstTop - (i / PER_ROW) * ROW_STEP;
            if (i >= slots) {
                lockedFrame(g, x, top);
                continue;
            }
            Meals.Meal<Identifier> meal = i < state.meals().active().size() ? state.meals().active().get(i) : null;
            boolean targeted = target != null && target.slot() == i;
            if (meal == null) {
                freeFrame(g, x, top);
                if (targeted) {
                    // the held food, dimmed, in the slot it would fill
                    g.item(held, x + 1, top + 1);
                    g.fill(x + 1, top + 1, x + FRAME - 1, top + FRAME - 1, 0x88000000);
                    marker(g, x, top, 0xFFFFFFFF);
                }
                continue;
            }
            long left = meal.remaining(now);
            long ending = Math.max(ENDING_TICKS, (meal.end() - meal.start()) / 10);
            boolean blinkOff = left < ending && (now / 5) % 2 == 0;
            frame(g, x, top, Tier.FRAME_GREY, 0x88000000);
            int lit = Math.round(RING.length * meal.fraction(now));
            for (int p = 0; p < RING.length; p++) {
                g.fill(x + RING[p][0], top + RING[p][1], x + RING[p][0] + 1, top + RING[p][1] + 1, ringColor(meal.tier(), p >= RING.length - lit, blinkOff));
            }
            BuiltInRegistries.ITEM.get(meal.dish()).ifPresent(item -> g.item(new ItemStack(item.value()), x + 1, top + 1));
            if (targeted && target.fill() == Meals.Fill.REPLACE) {
                g.fill(x + 1, top + 1, x + FRAME - 1, top + FRAME - 1, 0x77000000);   // the meal that would go, dimmed
                marker(g, x, top, REPLACE_COLOR);
            } else if (targeted) {
                marker(g, x, top, 0xFFFFFFFF);
            }
        }
        return true;
    }

    /** One ring pixel: the tier's colour where time is left, its track where it's gone, all of it white on a blink. */
    static int ringColor(Tier tier, boolean lit, boolean blinkOff) {
        return blinkOff ? BLINK_COLOR : lit ? tier.color() : tier.trackColor();
    }

    /**
     * Where the food in the player's hand would go if eaten now ({@link Meals#target}): a free slot, its own slot to
     * refresh, or the meal it would replace; null when the hand holds nothing that gives a meal.
     */
    public static Meals.Target preview(LocalPlayer player, FoodState state) {
        ItemStack held = heldMeal(player);
        if (held.isEmpty()) return null;
        // a slice fills its cake's slot, as eating it does
        return state.meals().target(BuiltInRegistries.ITEM.getKey(FoodProfiles.clientTastedAs(held.getItem())), state.slots());
    }

    /** The food in the main hand, else the off hand, that would give a meal; empty if neither would. */
    static ItemStack heldMeal(LocalPlayer player) {
        if (!FoodProfiles.clientReady() || player.hasEffect(MobEffects.HUNGER)) return ItemStack.EMPTY;
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.has(DataComponents.FOOD) && FoodProfiles.client(stack.getItem()).givesMeal()) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** A small arrow above the frame, pointing at the slot the held food would take (tier rings use every other colour). */
    static void marker(GuiGraphicsExtractor g, int x, int y, int color) {
        int cx = x + FRAME / 2;
        g.fill(cx - 3, y - 4, cx + 3, y - 3, color);
        g.fill(cx - 2, y - 3, cx + 2, y - 2, color);
        g.fill(cx - 1, y - 2, cx + 1, y - 1, color);
    }

    /** The frame of an 18×18 slot: a 1-pixel border in {@code border}, the inside in {@code inside}. */
    static void frame(GuiGraphicsExtractor g, int x, int y, int border, int inside) {
        g.fill(x + 1, y + 1, x + FRAME - 1, y + FRAME - 1, inside);
        g.fill(x, y, x + FRAME, y + 1, border);
        g.fill(x, y + FRAME - 1, x + FRAME, y + FRAME, border);
        g.fill(x, y + 1, x + 1, y + FRAME - 1, border);
        g.fill(x + FRAME - 1, y + 1, x + FRAME, y + FRAME - 1, border);
    }

    /** A free slot's plate seen from above: {@code o} its rim, {@code .} its well. */
    private static final String[] PLATE = {
            "      oooo      ",
            "    oooooooo    ",
            "   oooooooooo   ",
            "  oooo....oooo  ",
            "  ooo......ooo  ",
            "  ooo......ooo  ",
            "  ooo......ooo  ",
            "  ooo......ooo  ",
            "  oooo....oooo  ",
            "   oooooooooo   ",
            "    oooooooo    ",
            "      oooo      "};

    /** A free slot: a light frame with a faint plate in it. */
    static void freeFrame(GuiGraphicsExtractor g, int x, int y) {
        frame(g, x, y, 0xFF9A9A9A, 0x66000000);
        for (int row = 0; row < PLATE.length; row++) {
            for (int col = 0; col < 16; col++) {
                char c = PLATE[row].charAt(col);
                if (c != ' ') g.fill(x + 1 + col, y + 3 + row, x + 2 + col, y + 4 + row, c == 'o' ? 0x58FFFFFF : 0x22FFFFFF);
            }
        }
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
