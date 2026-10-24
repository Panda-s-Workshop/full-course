package com.panda.fullcourse.food;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.logic.Family;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** The item tags the scorer and the benefits read. Packs move any item between them. */
public final class FoodTags {
    /** Non-edible items the scorer follows down their recipes (wheat, sugar, intermediates). Edible items always are. */
    public static final TagKey<Item> CULINARY = tag("culinary");
    /** Bowls, bottles, buckets, sticks: ignored by the scorer. */
    public static final TagKey<Item> CONTAINERS = tag("containers");
    /** Obtained raw (crops, drops, fruit): always a leaf of the recipe chain. */
    public static final TagKey<Item> RAW_INGREDIENTS = tag("raw_ingredients");
    /** Their vanilla effects only, plus the heal: no meal slot. */
    public static final TagKey<Item> REMEDIES = tag("remedies");
    /** Placed meals: +3 score. */
    public static final TagKey<Item> PLACED_MEALS = tag("placed_meals");
    /** Soups always give Mending. */
    public static final TagKey<Item> SOUPS = tag("soups");
    /** One group per benefit family: {@code fullcourse:groups/<family>}. */
    public static final Map<Family, TagKey<Item>> GROUPS = groups();

    private FoodTags() {}

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, FullCourse.id(path));
    }

    private static Map<Family, TagKey<Item>> groups() {
        Map<Family, TagKey<Item>> map = new EnumMap<>(Family.class);
        for (Family f : Family.values()) map.put(f, tag("groups/" + f.key()));
        return map;
    }
}
