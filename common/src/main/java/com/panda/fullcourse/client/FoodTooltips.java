package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodTags;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.food.Profile;
import com.panda.fullcourse.kitchen.FeastBlock;
import com.panda.fullcourse.kitchen.RecipeHints;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.registry.ModItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Food tooltips: {@link FoodText}'s lines under the item's name, with the recipe chain while Shift is held, and for the
 * mod's own items how they're made ({@link RecipeHints}). A station says how it's used, and with Shift how it's
 * crafted (lang keys {@code tooltip.fullcourse.<item>.use} and {@code .craft}).
 */
public final class FoodTooltips {
    /** Treats Shift as held, for tests: the keyboard can't be pressed from one. */
    public static boolean shiftDown;
    /**
     * The widest line the mod adds, in GUI pixels. Vanilla doesn't wrap item tooltips (so neither does Fabric), and a
     * 427 px screen (GUI scale 3 at 720p, or 2 at 854 x 480) has to hold the tooltip beside the cursor.
     */
    public static final int MAX_WIDTH = 300;

    private FoodTooltips() {}

    public static void onTooltip(ItemStack stack, List<Component> lines) {
        if (stack.isEmpty() || lines.isEmpty() || !FoodProfiles.clientReady()) return; // a server without Full Course
        Minecraft mc = Minecraft.getInstance();
        boolean shift = shiftDown || mc.hasShiftDown();
        Profile p = FoodProfiles.client(stack.getItem());
        boolean food = FoodProfiles.isFood(stack.getItem()) || p != Profile.NONE;
        List<Component> made = new ArrayList<>(RecipeHints.lines(stack.getItem()));
        made.addAll(RecipeHints.sources(stack.getItem()));
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.getNamespace().equals(FullCourse.MODID)) {
            String key = "tooltip.fullcourse." + id.getPath();
            if (Language.getInstance().has(key + ".use")) lines.addAll(1, wrap(List.of(Component.translatable(key + ".use").withStyle(ChatFormatting.GRAY))));
            if (Language.getInstance().has(key + ".craft")) made.add(Component.translatable(key + ".craft").withStyle(ChatFormatting.DARK_AQUA));
        } else if (Language.getInstance().has("tooltip.fullcourse." + id.toLanguageKey() + ".use")
                // cake's line is the helping rule, which vanilla hunger replaces with the food bar
                && !(stack.is(net.minecraft.world.item.Items.CAKE) && FullCourseConfig.keepVanillaHunger())) {
            // what the mod changes about another's item (cake: one slice per helping)
            lines.addAll(1, wrap(List.of(Component.translatable("tooltip.fullcourse." + id.toLanguageKey() + ".use").withStyle(ChatFormatting.GRAY))));
        }
        int foodEnd = 1;
        if (food) {
            boolean tasted = mc.player != null
                    && Services.PLATFORM.foodState(mc.player).tasted().contains(BuiltInRegistries.ITEM.getKey(FoodProfiles.clientTastedAs(stack.getItem())));
            var component = stack.get(DataComponents.FOOD);
            // a feast heals per serving, not as an item
            int nutrition = component != null ? component.nutrition()
                    : stack.getItem() instanceof BlockItem b && b.getBlock() instanceof FeastBlock feast ? feast.serving() : 0;
            List<Component> foodLines = wrap(FoodText.lines(p, nutrition, shift, tasted));
            lines.addAll(1, foodLines);
            // above FoodText's "Shift: recipe chain" line, the last when Shift is up
            foodEnd = 1 + foodLines.size() - (!shift && p.steps() > 0 ? 1 : 0);
            // under the stars: an untasted dish that would move the next meal slot along, as the journal marks it
            if (!tasted && mc.player != null
                    && JournalScreen.countsToward(JournalScreen.nextSlot(Services.PLATFORM.foodState(mc.player)), FoodProfiles.clientTastedAs(stack.getItem()))) {
                lines.add(Math.min(2, lines.size()), Component.translatable("gui.fullcourse.journal.counts",
                        Services.PLATFORM.foodState(mc.player).slots() + 1).withStyle(ChatFormatting.GRAY));
            }
        } else if (!made.isEmpty() && !shift) {
            lines.add(1, Component.translatable("tooltip.fullcourse.shift_recipe").withStyle(ChatFormatting.GRAY));
        }
        // an ingredient says what it's for: the mod's dishes that take it
        if (id.getNamespace().equals(FullCourse.MODID) && !ModItems.dishes().contains(stack.getItem())) {
            int at = Math.min(foodEnd, lines.size());
            RecipeHints.usedIn(stack.getItem()).ifPresent(used -> lines.addAll(at, wrap(List.of(used))));
        }
        if (shift) lines.addAll(wrap(made));
        if (!FullCourseConfig.keepVanillaHunger()) markNeedsHunger(lines);
    }

    /**
     * {@code in} with each line wider than {@link #MAX_WIDTH} split at spaces, its continuations indented two spaces
     * past the line's own indent so they read as one entry. Only where the loader doesn't split lines itself (splitting
     * twice leaves single words), and only on the render thread: the recipe book's search builds tooltips on a worker,
     * where measuring a glyph not yet drawn would upload it from the wrong thread.
     */
    public static List<Component> wrap(List<? extends Component> in) {
        Minecraft mc = Minecraft.getInstance();
        if (Services.PLATFORM.wrapsTooltips() || !mc.isSameThread()) return List.copyOf(in);
        Font font = mc.font;
        List<Component> out = new ArrayList<>();
        for (Component line : in) {
            if (font.width(line) <= MAX_WIDTH) {
                out.add(line);
                continue;
            }
            String text = line.getString();
            String indent = " ".repeat(text.length() - text.stripLeading().length() + 2);
            List<FormattedText> parts = font.getSplitter().splitLines(line, MAX_WIDTH - font.width(indent), Style.EMPTY);
            for (int i = 0; i < parts.size(); i++) {
                MutableComponent part = Component.literal(i == 0 ? "" : indent);
                parts.get(i).visit((style, piece) -> {
                    part.append(Component.literal(piece).withStyle(style));
                    return Optional.empty();
                }, Style.EMPTY);
                out.add(part);
            }
        }
        return out;
    }

    /** Greys out each line naming an effect in {@code #fullcourse:needs_hunger}, and says it does nothing here. */
    static void markNeedsHunger(List<Component> lines) {
        Set<String> keys = new HashSet<>();
        BuiltInRegistries.MOB_EFFECT.getTagOrEmpty(FoodTags.NEEDS_HUNGER).forEach(h -> keys.add(h.value().getDescriptionId()));
        if (keys.isEmpty()) return;
        for (int i = 0; i < lines.size(); i++) {
            if (names(lines.get(i), keys)) {
                lines.set(i, lines.get(i).copy().withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable("tooltip.fullcourse.needs_hunger").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
    }

    /** The component, its arguments or its siblings name one of the translation keys. */
    private static boolean names(Component c, Set<String> keys) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (keys.contains(t.getKey())) return true;
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component a && names(a, keys)) return true;
            }
        }
        for (Component sibling : c.getSiblings()) {
            if (names(sibling, keys)) return true;
        }
        return false;
    }
}
