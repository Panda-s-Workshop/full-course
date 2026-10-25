package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.FullCourse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.panda.fullcourse.registry.ModItems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * How each Full Course item is made, for its Shift tooltip: "Cooking Pot: Chopped Vegetables, Chopped
 * Onion, Baked Potato, Broth, in a Bowl". Read from the mod's item table, so it shows the mod's own recipes; a data pack that changes them is better read in a recipe viewer.
 */
public final class RecipeHints {
    /** At most this many "where it comes from" lines under a recipe. */
    private static final int MAX_SOURCES = 4;
    private static Map<Item, List<Component>> hints;
    /** Each item's first recipe's ingredients, as item ids and tags. */
    private static final Map<Item, List<String>> firstRecipe = new HashMap<>();

    private RecipeHints() {}

    /** The item's recipe lines, empty for items that aren't the mod's or have no recipe. */
    public static List<Component> lines(Item item) {
        return hints().getOrDefault(item, List.of());
    }

    /** Loaded once, under a lock: the recipe book's search asks for tooltips from worker threads too. */
    private static synchronized Map<Item, List<Component>> hints() {
        if (hints == null) hints = load();
        return hints;
    }

    /**
     * Where the ingredients made first come from, so a dish can be traced to raw ingredients without a recipe viewer:
     * "Pastry — Crafting: any dough, Butter", then Pastry's own Butter and Dough. Breadth first, each once, at most
     * {@link #MAX_SOURCES} lines. A tag counts as the mod's own item in it (any dough: Dough).
     */
    public static List<Component> sources(Item item) {
        hints();
        List<Component> out = new ArrayList<>();
        Set<Item> seen = new LinkedHashSet<>(List.of(item));
        Deque<Item> queue = new ArrayDeque<>(made(item));
        while (!queue.isEmpty() && out.size() < MAX_SOURCES) {
            Item next = queue.poll();
            if (!seen.add(next)) continue;
            List<Component> recipe = lines(next);
            Component from = recipe.isEmpty() ? (wild(next) ? Component.translatable(useKey(next)) : null) : recipe.getFirst();
            if (from == null) continue;
            out.add(Component.translatable("hint.fullcourse.from", Component.translatable(next.getDescriptionId()), from)
                    .withStyle(ChatFormatting.GRAY));
            queue.addAll(made(next));
        }
        return out;
    }

    /** The mod's items among {@code item}'s first recipe's ingredients, tags resolved to the mod's own item in them. */
    private static List<Item> made(Item item) {
        List<Item> out = new ArrayList<>();
        for (String id : firstRecipe.getOrDefault(item, List.of())) {
            if (id.startsWith("#")) {
                var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(id.substring(1)));
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    if (holder.value() != item && traced(holder.value())) {
                        out.add(holder.value());
                        break;
                    }
                }
            } else {
                Item ingredient = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                if (traced(ingredient)) out.add(ingredient);
            }
        }
        return out;
    }

    /** Made by one of the mod's recipes, or one of its crops found growing wild. */
    private static boolean traced(Item item) {
        return hints().containsKey(item) || wild(item);
    }

    /** The mod's own raw ingredient with a line on where to find it (Onion: "Grows wild in plains..."). */
    private static boolean wild(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(FullCourse.MODID) && Language.getInstance().has(useKey(item));
    }

    private static String useKey(Item item) {
        return "tooltip.fullcourse." + BuiltInRegistries.ITEM.getKey(item).getPath() + ".use";
    }

    private static Map<Item, List<Component>> load() {
        Map<Item, List<Component>> out = new HashMap<>();
        for (JsonElement el : ModItems.table()) {
            JsonObject e = el.getAsJsonObject();
            if (!e.has("made")) continue;
            List<Component> lines = new ArrayList<>();
            for (JsonElement m : e.getAsJsonArray("made")) {
                JsonObject made = m.getAsJsonObject();
                MutableComponent from = Component.empty();
                var ingredients = made.getAsJsonArray("from");
                for (int i = 0; i < ingredients.size(); i++) {
                    if (i > 0) from.append(Component.literal(", "));
                    from.append(ingredient(ingredients.get(i)));
                }
                if (made.has("container")) from.append(Component.translatable("hint.fullcourse.served_in", name(made.get("container").getAsString())));
                lines.add(Component.translatable("hint.fullcourse.made", Component.translatable("station.fullcourse." + made.get("station").getAsString()), from)
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            Item item = ModItems.item(e.get("id").getAsString());
            out.put(item, List.copyOf(lines));
            List<String> first = new ArrayList<>();
            for (JsonElement i : e.getAsJsonArray("made").get(0).getAsJsonObject().getAsJsonArray("from")) {
                if (i.isJsonPrimitive()) first.add(i.getAsString());
            }
            firstRecipe.put(item, List.copyOf(first));
        }
        return out;
    }

    /** An item's name, a tag's ("any raw meat"), or alternatives joined by " or ". */
    private static Component ingredient(JsonElement el) {
        if (el.isJsonArray()) {
            MutableComponent any = Component.empty();
            for (int i = 0; i < el.getAsJsonArray().size(); i++) {
                if (i > 0) any.append(Component.translatable("hint.fullcourse.or"));
                any.append(ingredient(el.getAsJsonArray().get(i)));
            }
            return any;
        }
        return name(el.getAsString());
    }

    private static Component name(String id) {
        if (id.startsWith("#")) return Component.translatable("hint.fullcourse.tag." + id.substring(1).replace(':', '.').replace('/', '.'));
        return Component.translatable(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)).getDescriptionId());
    }
}
