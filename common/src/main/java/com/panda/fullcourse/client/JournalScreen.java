package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.food.Profile;
import com.panda.fullcourse.food.Tasting;
import com.panda.fullcourse.logic.Milestones;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.platform.Services;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Tasting Journal's screen: every dish the server rates (the mod's, vanilla's, and other mods' of two stars or
 * more, plus anything already tasted), by tier, in a grid; tasted dishes in colour inside a frame of their tier's
 * colour, the rest faded, with a pip on those whose tasting would move the next slot along. Hovering a dish shows its
 * tooltip (tier, benefits, and with Shift the recipe chain). The next meal slot's milestone and a bar sit at the top, and
 * a search box beside the title narrows the grid by name or, with "@", by mod. Reads only the synced food state: nothing
 * is sent.
 */
public class JournalScreen extends Screen {
    private static final Identifier TEXTURE = FullCourse.id("textures/gui/journal.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_OFF = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    /** The layout drawn into textures/gui/journal.png: panel, bar, grid and scrollbar track. */
    public static final int W = 212, H = 194, GRID_X = 8, GRID_Y = 46, COLS = 10, ROWS = 7, BAR_X = 8, BAR_Y = 30, BAR_W = 196,
            BAR_H = 4, SCROLL_X = 195, SCROLL_Y = 46, SCROLL_H = ROWS * 18 - 2;
    private static final int LABEL = 0xFF404040, FADE = 0xB08B8B8B, WELL = 0xFF3C3C3C, PIP_EDGE = 0xFF202020, SEARCH_W = 92;

    /** Every dish the journal lists, and those the search lets through (what the grid shows). */
    private final List<Item> all = new ArrayList<>();
    private final List<Item> dishes = new ArrayList<>();
    private EditBox search;
    /** A fixed list to show instead of the server's dishes (for checks: a pack with more dishes than one page). */
    private final List<Item> fixed;
    private int left, top, scroll;
    private boolean dragging;

    public JournalScreen() {
        this(null);
    }

    public JournalScreen(List<Item> fixed) {
        super(Component.translatable("item.fullcourse.tasting_journal"));
        this.fixed = fixed == null ? null : List.copyOf(fixed);
    }

    public static void open() {
        Minecraft.getInstance().gui.setScreen(new JournalScreen());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        all.clear();
        all.addAll(fixed != null ? fixed : dishes(state().tasted()));
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, left + W - 8 - SEARCH_W, top + 4, SEARCH_W, 12, Component.translatable("gui.fullcourse.journal.search"));
        search.setHint(Component.translatable("gui.fullcourse.journal.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(text -> filter());
        addRenderableWidget(search);
        filter();
    }

    /** The grid shows the dishes whose name holds the search, or with "@", those of a mod whose id or name starts so. */
    private void filter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        dishes.clear();
        for (Item item : all) if (matches(item, query)) dishes.add(item);
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    static boolean matches(Item item, String query) {
        if (query.isEmpty()) return true;
        if (query.startsWith("@")) {
            String mod = query.substring(1);
            String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            return ns.startsWith(mod) || Services.PLATFORM.modName(ns).toLowerCase(Locale.ROOT).startsWith(mod);
        }
        return item.getName(new ItemStack(item)).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    /** Sets the search, as typing it would (for checks). */
    public void search(String text) {
        search.setValue(text);
    }

    /** A grid tile on screen: its dish and the top-left of its 16×16 icon. */
    public record Tile(Item item, int x, int y) {}

    /** The tile under the mouse, if any (recipe viewers use it to show a dish's recipes and uses). */
    public Optional<Tile> tileAt(double mouseX, double mouseY) {
        for (int i = scroll * COLS; i < dishes.size() && i < (scroll + ROWS) * COLS; i++) {
            int x = left + GRID_X + (i % COLS) * 18, y = top + GRID_Y + (i / COLS - scroll) * 18;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return Optional.of(new Tile(dishes.get(i), x, y));
        }
        return Optional.empty();
    }

    public int left() {
        return left;
    }

    public int top() {
        return top;
    }

    private FoodState state() {
        return minecraft.player == null ? FoodState.EMPTY : Services.PLATFORM.foodState(minecraft.player);
    }

    /** The journal's dishes: lowest tier first, the mod's before vanilla's before other mods', then by name. */
    static List<Item> dishes(Set<Identifier> tasted) {
        List<Item> out = new ArrayList<>();
        for (Map.Entry<Item, Profile> e : FoodProfiles.clientAll().entrySet()) {
            Profile p = e.getValue();
            if (!p.givesMeal()) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(e.getKey());
            String ns = id.getNamespace();
            if (ns.equals(FullCourse.MODID) || ns.equals("minecraft") || p.tier().stars() >= Tier.PREPARED.stars() || tasted.contains(id)) {
                out.add(e.getKey());
            }
        }
        out.sort(Comparator.<Item>comparingInt(i -> FoodProfiles.client(i).tier().stars())
                .thenComparingInt(i -> source(BuiltInRegistries.ITEM.getKey(i).getNamespace()))
                .thenComparing(i -> i.getName(new ItemStack(i)).getString()));
        return out;
    }

    private static int source(String namespace) {
        return namespace.equals(FullCourse.MODID) ? 0 : namespace.equals("minecraft") ? 1 : 2;
    }

    private int rows() {
        return (dishes.size() + COLS - 1) / COLS;
    }

    private int maxScroll() {
        return Math.max(0, rows() - ROWS);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top, 0, 0, W, H, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        FoodState state = state();
        Set<Identifier> tasted = state.tasted();
        g.text(font, title, left + 8, top + 6, LABEL, false);
        milestone(g, state, mouseX, mouseY);

        Milestones.Next next = nextSlot(state);
        Item hovered = null;
        for (int i = scroll * COLS; i < dishes.size() && i < (scroll + ROWS) * COLS; i++) {
            Item item = dishes.get(i);
            int x = left + GRID_X + (i % COLS) * 18, y = top + GRID_Y + (i / COLS - scroll) * 18;
            boolean has = tasted.contains(BuiltInRegistries.ITEM.getKey(item));
            if (has) {
                // a dark well under the tier's frame, so the light iron of two stars shows on the grey panel too
                g.fill(x - 1, y - 1, x + 17, y + 17, WELL);
                g.outline(x - 1, y - 1, 18, 18, FoodProfiles.client(item).tier().color());
            }
            g.item(new ItemStack(item), x, y);
            if (!has) {
                g.fill(x, y, x + 16, y + 16, FADE);
                // a pip in the progress bar's colour: tasting this one moves the next slot along
                if (countsToward(next, item)) {
                    g.fill(x + 11, y, x + 16, y + 5, PIP_EDGE);
                    g.fill(x + 12, y + 1, x + 15, y + 4, barTier(next).color());
                }
            }
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
                hovered = item;
                g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            }
        }
        int sx = left + SCROLL_X, sy = top + SCROLL_Y;
        int knob = maxScroll() == 0 ? 0 : Math.round((SCROLL_H - 15) * (float) scroll / maxScroll());
        g.blitSprite(RenderPipelines.GUI_TEXTURED, maxScroll() == 0 ? SCROLLER_OFF : SCROLLER, sx, sy + knob, 12, 15);

        int count = 0;
        for (Item item : dishes) if (tasted.contains(BuiltInRegistries.ITEM.getKey(item))) count++;
        Component footer = Component.translatable("gui.fullcourse.journal.count", count, dishes.size());
        g.text(font, footer, left + (W - font.width(footer)) / 2, top + H - 14, LABEL, false);

        if (hovered != null) {
            ItemStack stack = new ItemStack(hovered);
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, stack));
            if (!tasted.contains(BuiltInRegistries.ITEM.getKey(hovered))) {
                lines.add(Math.min(1, lines.size()), Component.translatable("gui.fullcourse.journal.untasted").withStyle(ChatFormatting.DARK_GRAY));
                if (countsToward(next, hovered)) {
                    lines.add(Math.min(2, lines.size()), Component.translatable("gui.fullcourse.journal.counts", next.slot()).withStyle(ChatFormatting.GRAY));
                }
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /** The next slot to earn, or {@code NONE} once there is none left (all earned, or the config's maximum reached). */
    public static Milestones.Next nextSlot(FoodState state) {
        Milestones.Next next = Milestones.next(Tasting.clientCount(state.tasted()), state.bonusSlots(), FullCourseConfig.slotMilestones());
        return state.slots() >= FullCourseConfig.maxMealSlots() ? Milestones.Next.NONE : next;
    }

    /** An untasted dish whose tasting would move {@code next} along ({@link Milestones#counts}). */
    public static boolean countsToward(Milestones.Next next, Item item) {
        var profile = FoodProfiles.client(item);
        return profile.givesMeal() && Milestones.counts(next, profile.tier());
    }

    /** The tier whose colour the progress bar (and the pips) use. */
    private static Tier barTier(Milestones.Next next) {
        return Tier.ofStars(next.stars());
    }

    /** "Meal slot 4" and "9 / 15 ★★★", a bar in the counted tier's colour, and the whole rule on hover. */
    private void milestone(GuiGraphicsExtractor g, FoodState state, int mouseX, int mouseY) {
        Milestones.Count count = Tasting.clientCount(state.tasted());
        Milestones.Next next = Milestones.next(count, state.bonusSlots(), FullCourseConfig.slotMilestones());
        int y = top + 18;
        if (next == Milestones.Next.NONE || state.slots() >= FullCourseConfig.maxMealSlots()) {
            Component all = Component.translatable("gui.fullcourse.journal.all");
            g.text(font, all, left + 8, y, LABEL, false);
            g.fill(left + BAR_X, top + BAR_Y, left + BAR_X + BAR_W, top + BAR_Y + BAR_H, Tier.FEAST.color());
            return;
        }
        int slot = state.slots() + 1;
        Tier counted = Tier.ofStars(next.stars());
        g.text(font, Component.translatable("gui.fullcourse.journal.slot", slot), left + 8, y, LABEL, false);
        Component progress = Component.translatable(next.needsFeast() ? "gui.fullcourse.journal.progress_feast" : "gui.fullcourse.journal.progress",
                Math.min(next.have(), next.need()), next.need(), "★".repeat(counted.stars()));
        g.text(font, progress, left + W - 8 - font.width(progress), y, LABEL, false);
        float done = Math.min(1F, next.have() / (float) next.need());
        if (next.needsFeast()) done = Math.min(done, 0.95F);
        g.fill(left + BAR_X, top + BAR_Y, left + BAR_X + Math.round(BAR_W * done), top + BAR_Y + BAR_H, counted.color());
        if (mouseX >= left + 8 && mouseX < left + W - 8 && mouseY >= y - 1 && mouseY < top + BAR_Y + BAR_H + 1) {
            Component rule = Component.translatable(next.needsFeast() ? "gui.fullcourse.journal.rule_feast" : "gui.fullcourse.journal.rule",
                    slot, next.need(), "★".repeat(counted.stars()));
            g.setTooltipForNextFrame(font, rule, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int before = scroll;
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return scroll != before || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - left, y = event.y() - top;
        if (event.button() == 0 && maxScroll() > 0 && x >= SCROLL_X && x < SCROLL_X + 12 && y >= SCROLL_Y && y < SCROLL_Y + SCROLL_H) {
            dragging = true;
            drag(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            drag(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    private void drag(double mouseY) {
        float at = (float) (mouseY - top - SCROLL_Y - 7.5) / (SCROLL_H - 15);
        scroll = Mth.clamp(Math.round(at * maxScroll()), 0, maxScroll());
    }

    /** The first row shown, for checks. */
    public int scroll() {
        return scroll;
    }

    public List<Item> shown() {
        return List.copyOf(dishes);
    }
}
