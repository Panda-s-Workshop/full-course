package com.panda.fullcourse.food;

import com.panda.fullcourse.logic.Milestones;
import com.panda.fullcourse.logic.Tier;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The tasted set counted for the slot milestones, by each dish's current tier: a datapack that rates a dish
 * higher or lower moves it between counts, and a dish whose mod is gone counts for nothing.
 */
public final class Tasting {
    private Tasting() {}

    /** {@code tasted} counted with {@code profiles} (the server's or the client's table). */
    public static Milestones.Count count(Set<Identifier> tasted, Function<Item, Profile> profiles) {
        int prepared = 0, hearty = 0;
        boolean feast = false;
        for (Identifier id : tasted) {
            Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(id);
            if (item.isEmpty()) continue;
            Profile p = profiles.apply(item.get().value());
            if (!p.givesMeal()) continue;
            int stars = p.tier().stars();
            if (stars >= Tier.PREPARED.stars()) prepared++;
            if (stars >= Tier.HEARTY.stars()) hearty++;
            if (p.tier() == Tier.FEAST) feast = true;
        }
        return new Milestones.Count(prepared, hearty, feast);
    }

    /** The client's count, from the table the server sent. */
    public static Milestones.Count clientCount(Set<Identifier> tasted) {
        return count(tasted, FoodProfiles::client);
    }
}
