package com.panda.fullcourse.food;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.logic.Tier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * The advancements the mod's own rules grant: {@code first_meal} for any meal, {@code hearty} for a
 * three-star one, {@code cutting_board} for the first cut. Their JSON uses the impossible
 * trigger; only this awards them.
 */
public final class FoodAdvancements {
    private FoodAdvancements() {}

    static void onMeal(ServerPlayer player, Tier tier) {
        award(player, "first_meal");
        if (tier.stars() >= Tier.HEARTY.stars()) award(player, "hearty");
    }

    public static void award(ServerPlayer player, String key) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(FullCourse.id(key));
        if (holder != null) player.getAdvancements().award(holder, "done");
    }
}
