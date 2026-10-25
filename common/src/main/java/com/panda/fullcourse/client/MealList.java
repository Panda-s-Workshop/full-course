package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.food.Tasting;
import com.panda.fullcourse.logic.Meals;
import com.panda.fullcourse.logic.Milestones;
import com.panda.fullcourse.logic.Recovery;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.registry.ModAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;

/**
 * The meals listed beside the survival inventory, in the space the recipe book takes when open (so it hides then):
 * each active meal with its dish, time left and benefits, the free slots, and the next slot's milestone with a bar.
 * Hovering a meal shows the dish's tooltip. Drawn at the end of the inventory screen's own frame (a client mixin).
 */
public final class MealList {
    private static final int MAX_W = 156, MIN_W = 104, ROW = 22, PAD = 4, GAP = 4;
    private static final int BG = 0xD0100010, BORDER = 0xFF2A1A40, TEXT = 0xFFFFFFFF, DIM = 0xFFA0A0A0;

    /** Where the list was drawn last frame; null while it isn't shown. */
    private static Rect2i shown;

    private MealList() {}

    /** The list's area on the inventory screen, for recipe viewers to keep clear; null while it isn't shown. */
    public static Rect2i area() {
        return shown;
    }

    public static void extract(GuiGraphicsExtractor g, InventoryScreen screen, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        shown = null;
        // shown with vanilla hunger kept too: meals still give their benefits, and this is where their time left shows
        if (player == null || !FoodProfiles.clientReady()) return;
        // the recipe book moves the inventory off centre and takes this space
        if (screen.leftPos != (screen.width - 176) / 2) return;
        int w = Math.min(MAX_W, screen.leftPos - 2 * GAP);
        if (w < MIN_W) return;
        Font font = mc.font;
        FoodState state = Services.PLATFORM.foodState(player);
        long now = player.level().getGameTime();
        List<Meals.Meal<Identifier>> meals = state.meals().active();
        int free = Math.max(0, state.slots() - meals.size());
        Milestones.Next next = Milestones.next(Tasting.clientCount(state.tasted()), state.bonusSlots(), FullCourseConfig.slotMilestones());
        boolean more = next != Milestones.Next.NONE && state.slots() < FullCourseConfig.maxMealSlots();
        List<FormattedCharSequence> recoveryLines = new ArrayList<>();
        Component recovery = recoveryLine(player);
        if (recovery != null && font.width(recovery) > w - 2 * PAD && recoveryInterval(player) > 0) {
            // split where it reads, not before the unit
            recoveryLines.addAll(font.split(Component.translatable("gui.fullcourse.meals.recovery_heal"), w - 2 * PAD));
            recoveryLines.addAll(font.split(Component.translatable("gui.fullcourse.meals.recovery_every",
                    FoodText.number(recoveryInterval(player) / 20.0)), w - 2 * PAD));
        } else if (recovery != null) {
            recoveryLines.addAll(font.split(recovery, w - 2 * PAD));
        }
        Component delay = recovery == null ? null : recoveryDelayLine(player);
        if (delay != null) recoveryLines.addAll(font.split(delay, w - 2 * PAD));

        int tx = PAD + 20, room = w - tx - PAD;
        List<List<FormattedCharSequence>> lines = new ArrayList<>();
        List<Integer> rowHeights = new ArrayList<>();
        for (Meals.Meal<Identifier> meal : meals) {
            List<FormattedCharSequence> wrapped = font.split(Component.literal(benefits(meal, now)), room);
            lines.add(wrapped);
            rowHeights.add(Math.max(ROW, 12 + 10 * wrapped.size()));
        }
        int fixed = PAD + 12 + (free > 0 && !meals.isEmpty() ? 11 : 0) + (recoveryLines.isEmpty() ? 0 : 10 * recoveryLines.size() + 1) + (more ? 22 : 0) + PAD - 2;
        // as many meals as the screen holds; the rest are counted in one line
        int shownMeals = meals.size(), rows = rowHeights.stream().mapToInt(Integer::intValue).sum();
        while (shownMeals > 1 && fixed + rows + (shownMeals < meals.size() ? 11 : 0) > screen.height - 2 * GAP) {
            rows -= rowHeights.get(--shownMeals);
        }
        int hidden = meals.size() - shownMeals;
        int h = fixed + (meals.isEmpty() ? ROW : rows) + (hidden > 0 ? 11 : 0);
        int x = screen.leftPos - GAP - w, y = Math.max(GAP, Math.min(screen.topPos, screen.height - GAP - h));
        shown = new Rect2i(x, y, w, h);
        g.fill(x, y, x + w, y + h, BG);
        g.outline(x, y, w, h, BORDER);

        int cy = y + PAD;
        g.text(font, Component.translatable("gui.fullcourse.meals.title", meals.size(), state.slots()), x + PAD, cy, TEXT, true);
        cy += 12;
        if (meals.isEmpty()) {
            List<FormattedCharSequence> none = font.split(Component.translatable("gui.fullcourse.meals.none"), w - 2 * PAD);
            for (int l = 0; l < Math.min(2, none.size()); l++) g.text(font, none.get(l), x + PAD, cy + 1 + 10 * l, DIM, false);
            cy += ROW;
        }
        ItemStack hovered = null;
        for (int i = 0; i < shownMeals; i++) {
            Meals.Meal<Identifier> meal = meals.get(i);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(meal.dish()));
            g.item(stack, x + PAD, cy + 2);
            g.text(font, fit(font, stack.getHoverName().getString(), room), x + tx, cy + 1, meal.tier().color(), false);
            List<FormattedCharSequence> wrapped = lines.get(i);
            for (int l = 0; l < wrapped.size(); l++) g.text(font, wrapped.get(l), x + tx, cy + 11 + 10 * l, DIM, false);
            int rowH = Math.max(ROW, 12 + 10 * wrapped.size());
            if (mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + rowH) hovered = stack;
            cy += rowH;
        }
        if (hidden > 0) {
            g.text(font, Component.translatable("gui.fullcourse.meals.more", hidden), x + PAD, cy, DIM, false);
            cy += 11;
        }
        if (free > 0 && !meals.isEmpty()) {
            g.text(font, Component.translatable(free == 1 ? "gui.fullcourse.meals.free_one" : "gui.fullcourse.meals.free", free), x + PAD, cy, DIM, false);
            cy += 11;
        }
        for (FormattedCharSequence line : recoveryLines) {
            g.text(font, line, x + PAD, cy, DIM, false);
            cy += 10;
        }
        if (!recoveryLines.isEmpty()) cy += 1;
        if (more) {
            Tier counted = Tier.ofStars(next.stars());
            Component line = Component.translatable(next.needsFeast() ? "gui.fullcourse.meals.next_feast" : "gui.fullcourse.meals.next",
                    state.slots() + 1, Math.min(next.have(), next.need()), next.need(), "★".repeat(counted.stars()));
            g.text(font, fit(font, line.getString(), w - 2 * PAD), x + PAD, cy + 2, TEXT, false);
            float done = Math.min(next.needsFeast() ? 0.95F : 1F, next.have() / (float) next.need());
            g.fill(x + PAD, cy + 13, x + w - PAD, cy + 16, 0xFF3A3A3A);
            g.fill(x + PAD, cy + 13, x + PAD + Math.round((w - 2 * PAD) * done), cy + 16, counted.color());
            // the rule behind the numbers, as the journal says it
            if (mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + 18) {
                Component rule = Component.translatable(next.needsFeast() ? "gui.fullcourse.journal.rule_feast" : "gui.fullcourse.journal.rule",
                        state.slots() + 1, next.need(), "★".repeat(counted.stars()));
                g.setTooltipForNextFrame(font, font.split(rule, 200), mouseX, mouseY);
            }
        }
        if (hovered != null) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    /**
     * "Recovery: ½ ❤ every 4 s" (each Recovery heal is 1 HP) at the pace {@code fullcourse:recovery_rate} (Mending,
     * another mod, a command) sets, "Recovery: off" when it heals nothing; null while vanilla hunger is kept.
     */
    public static Component recoveryLine(LocalPlayer player) {
        if (FullCourseConfig.keepVanillaHunger()) return null;
        int interval = recoveryInterval(player);
        if (interval <= 0) return Component.translatable("gui.fullcourse.meals.recovery_off");
        return Component.translatable("gui.fullcourse.meals.recovery", FoodText.number(interval / 20.0));
    }

    /** "Starts 8 s after a hit", under {@link #recoveryLine} while Recovery heals and waits for a delay; else null. */
    public static Component recoveryDelayLine(LocalPlayer player) {
        int delay = FullCourseConfig.recoveryDelayTicks();
        if (FullCourseConfig.keepVanillaHunger() || delay <= 0 || recoveryInterval(player) <= 0) return null;
        return Component.translatable("gui.fullcourse.meals.recovery_delay", FoodText.number(delay / 20.0));
    }

    private static int recoveryInterval(LocalPlayer player) {
        if (!FullCourseConfig.regenerates()) return 0;
        AttributeInstance attribute = player.getAttribute(ModAttributes.RECOVERY_RATE.holder());
        return Recovery.interval(FullCourseConfig.recoveryIntervalTicks(), attribute == null ? 1 : attribute.getValue());
    }

    /** "19:59 · Brawn III, Vigor III, Mending III": time left, then each family at its level, as the action bar says it. */
    private static String benefits(Meals.Meal<Identifier> meal, long now) {
        String level = Component.translatable("enchantment.level." + meal.level()).getString();
        String names = meal.families().stream().map(f -> Component.translatable("family.fullcourse." + f.key()).getString() + " " + level)
                .collect(Collectors.joining(", "));
        String time = FoodText.duration((int) Math.min(Integer.MAX_VALUE, meal.remaining(now)));
        return time + " · " + names;
    }

    /** {@code s} cut to {@code width} with an ellipsis. */
    private static String fit(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, width - font.width("…")) + "…";
    }
}
