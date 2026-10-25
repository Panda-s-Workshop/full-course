package com.panda.fullcourse.food;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.registry.ModItems;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * The advancements the mod's own rules grant: {@code first_meal} for any meal, {@code hearty}, {@code gourmet} and
 * {@code first_feast} by the meal's tier, {@code taste_simple} once every one-star dish of the mod is tasted and
 * {@code taste_all} for all of them, {@code slot_3..5} with the slots ({@link FoodRules}), {@code cutting_board} for
 * the first cut. Their JSON uses the impossible trigger; only this awards them.
 */
public final class FoodAdvancements {
    private FoodAdvancements() {}

    /** A meal taken, by its tier. */
    static void onMeal(ServerPlayer player, Tier tier) {
        award(player, "first_meal");
        if (tier.stars() >= Tier.HEARTY.stars()) award(player, "hearty");
        if (tier.stars() >= Tier.GOURMET.stars()) award(player, "gourmet");
        if (tier == Tier.FEAST) award(player, "first_feast");
    }

    /** A dish tasted, whether or not it took a meal slot: the tasting advancements count every dish eaten. */
    static void onTasted(ServerPlayer player, Set<Identifier> tasted) {
        List<Item> dishes = ModItems.dishes();
        boolean all = true, simple = true;
        for (Item dish : dishes) {
            if (tasted.contains(BuiltInRegistries.ITEM.getKey(dish))) continue;
            all = false;
            if (FoodProfiles.get(player.level().getServer(), dish).tier() == Tier.SIMPLE) simple = false;
        }
        if (simple) award(player, "taste_simple");
        if (all) award(player, "taste_all");
    }

    public static void award(ServerPlayer player, String key) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(FullCourse.id(key));
        if (holder != null) player.getAdvancements().award(holder, "done");
    }
}
