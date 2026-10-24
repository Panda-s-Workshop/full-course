package com.panda.fullcourse.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.food.TastingJournalItem;
import com.panda.fullcourse.platform.registry.DeferredItem;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;

/**
 * The mod's items. The kitchen blocks are written here; every food, ingredient, crop and knife comes from
 * the table in {@code fullcourse/items.json}.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FullCourse.MODID);
    public static final String TABLE_PATH = "/fullcourse/items.json";
    /** Flint knives: stone-like, 64 uses, mended with flint. */
    private static final ToolMaterial FLINT = new ToolMaterial(BlockTags.INCORRECT_FOR_STONE_TOOL, 64, 4.0F, 1.0F, 5,
            TagKey.create(Registries.ITEM, FullCourse.id("repairs_flint_knife")));
    /** Dishes stack like honey bottles: a few servings to carry, not a chest's worth. */
    public static final int DISH_STACK = 16;

    public static final DeferredItem<BlockItem> CUTTING_BOARD = ITEMS.registerSimpleBlockItem(ModBlocks.CUTTING_BOARD);
    public static final DeferredItem<BlockItem> COOKING_POT = ITEMS.registerSimpleBlockItem(ModBlocks.COOKING_POT);
    public static final DeferredItem<BlockItem> BRICK_OVEN = ITEMS.registerSimpleBlockItem(ModBlocks.BRICK_OVEN);
    public static final DeferredItem<BlockItem> FERMENTING_CROCK = ITEMS.registerSimpleBlockItem(ModBlocks.FERMENTING_CROCK);
    /** The table's items by path, in the table's order. */
    public static final Map<String, DeferredItem<Item>> TABLE = registerTable();
    private static List<Item> dishes;

    private ModItems() {}

    /** The mod's dishes and feasts, in the table's order: what the tasting advancements ask for. */
    public static synchronized List<Item> dishes() {
        if (dishes == null) {
            List<Item> out = new ArrayList<>();
            for (JsonElement el : table()) {
                JsonObject e = el.getAsJsonObject();
                String kind = e.get("kind").getAsString();
                if ("dish".equals(kind) || "feast".equals(kind)) out.add(item(e.get("id").getAsString()));
            }
            dishes = List.copyOf(out);
        }
        return dishes;
    }


    /** A table item by path ("onion"). */
    public static Item item(String path) {
        DeferredItem<Item> holder = TABLE.get(path);
        if (holder == null) throw new IllegalArgumentException("No Full Course item " + path);
        return holder.get();
    }

    /** The item table, read from the jar. */
    public static JsonArray table() {
        return Table.read();
    }

    /** Reads the table without registering the items, so blocks can take their numbers from it first. */
    public static final class Table {
        private Table() {}

        public static JsonArray read() {
            try (InputStream in = ModItems.class.getResourceAsStream(TABLE_PATH)) {
                if (in == null) throw new IllegalStateException("Missing " + TABLE_PATH);
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            } catch (IOException e) {
                throw new IllegalStateException("Couldn't read " + TABLE_PATH, e);
            }
        }

        /** A table entry by path. */
        public static JsonObject entry(String path) {
            for (JsonElement el : read()) {
                if (path.equals(el.getAsJsonObject().get("id").getAsString())) return el.getAsJsonObject();
            }
            throw new IllegalArgumentException("No Full Course item " + path);
        }
    }

    private static Map<String, DeferredItem<Item>> registerTable() {
        Map<String, DeferredItem<Item>> out = new LinkedHashMap<>();
        for (JsonElement el : table()) {
            JsonObject e = el.getAsJsonObject();
            String id = e.get("id").getAsString();
            String kind = e.get("kind").getAsString();
            UnaryOperator<Item.Properties> props = p -> properties(p, e);
            DeferredItem<Item> holder = switch (kind) {
                case "tool" -> ITEMS.registerItem(id, Item::new, p -> props.apply(p.sword("iron".equals(e.get("material").getAsString())
                        ? ToolMaterial.IRON : FLINT, 1.0F, -2.0F)));
                case "crop" -> ITEMS.<Item>registerItem(id, p -> new BlockItem(BuiltInRegistries.BLOCK.getValue(FullCourse.id(e.get("block").getAsString())), p),
                        p -> props.apply(p.useItemDescriptionPrefix()));
                case "feast" -> ITEMS.<Item>registerItem(id, p -> new BlockItem(BuiltInRegistries.BLOCK.getValue(FullCourse.id(e.get("block").getAsString())), p),
                        p -> props.apply(p.useItemDescriptionPrefix().stacksTo(1)));
                case "journal" -> ITEMS.registerItem(id, TastingJournalItem::new, p -> props.apply(p.stacksTo(1)));
                default -> ITEMS.registerItem(id, Item::new, props);
            };
            out.put(id, holder);
        }
        return Collections.unmodifiableMap(out);
    }

    /** Food, the container eating leaves (dishes) or crafting leaves (ingredients), stack size. */
    private static Item.Properties properties(Item.Properties p, JsonObject e) {
        Item container = e.has("container") ? BuiltInRegistries.ITEM.getValue(Identifier.parse(e.get("container").getAsString())) : null;
        if (e.has("nutrition")) {
            FoodProperties food = new FoodProperties(e.get("nutrition").getAsInt(), e.get("saturation").getAsFloat(), false);
            boolean drink = e.has("drink") && e.get("drink").getAsBoolean();
            p.food(food, drink ? Consumables.DEFAULT_DRINK : Consumables.DEFAULT_FOOD);
        }
        if ("dish".equals(e.get("kind").getAsString())) {
            p.stacksTo(DISH_STACK);
            if (container != null) p.usingConvertsTo(container);
        } else if (container != null) {
            p.craftRemainder(container);
        }
        return p;
    }
}
