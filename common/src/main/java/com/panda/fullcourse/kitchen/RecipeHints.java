package com.panda.fullcourse.kitchen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.panda.fullcourse.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * How each Full Course item is made, for its Shift tooltip: "Cooking Pot: Chopped Vegetables, Chopped
 * Onion, Baked Potato, Broth, in a Bowl". Read from the mod's item table, so it shows the mod's own recipes; a data pack that changes them is better read in a recipe viewer.
 */
public final class RecipeHints {
    private static Map<Item, List<Component>> hints;

    private RecipeHints() {}

    /** The item's recipe lines, empty for items that aren't the mod's or have no recipe. */
    public static List<Component> lines(Item item) {
        if (hints == null) hints = load();
        return hints.getOrDefault(item, List.of());
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
            out.put(ModItems.item(e.get("id").getAsString()), List.copyOf(lines));
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
