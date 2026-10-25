package com.panda.fullcourse.food;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.logic.Meals;
import com.panda.fullcourse.logic.Recovery;
import com.panda.fullcourse.logic.Tier;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * One player's food data: held meals, the heal-over-time still to come, the dishes tasted and the
 * meal slots unlocked by the journal. Saved with the player, kept on death (meals are cleared on death unless the
 * config keeps them), synced to the owning player.
 *
 * @param nourish HP the last meal will still heal, 1 per second
 * @param bonusSlots meal slots unlocked beyond the starting ones
 * @param helpings the game time each dish last started a meal, kept while it can still refuse a helping: a feast,
 *     cake or pie's helping rule reads it, so a meal replaced, lost on death or cut short doesn't free the next one
 */
public record FoodState(Meals<Identifier> meals, int nourish, Set<Identifier> tasted, int bonusSlots, Map<Identifier, Long> helpings) {
    public static final FoodState EMPTY = new FoodState(Meals.empty(), 0, Set.of(), 0, Map.of());
    /** Caps what a save or packet can hold. */
    public static final int MAX_TASTED = 4096;

    private static final Codec<Meals.Meal<Identifier>> MEAL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("dish").forGetter(Meals.Meal::dish),
            Codec.intRange(0, 5).fieldOf("tier").forGetter(m -> m.tier().stars()),
            Codec.STRING.listOf().fieldOf("families").forGetter(m -> m.families().stream().map(Family::key).toList()),
            Codec.intRange(0, 9).fieldOf("level").forGetter(Meals.Meal::level),
            Codec.LONG.fieldOf("start").forGetter(Meals.Meal::start),
            Codec.LONG.fieldOf("end").forGetter(Meals.Meal::end)
    ).apply(i, (dish, tier, families, level, start, end) -> new Meals.Meal<>(dish, Tier.ofStars(tier),
            families.stream().flatMap(k -> Family.byKey(k).stream()).toList(), level, start, end)));

    public static final Codec<FoodState> CODEC = RecordCodecBuilder.create(i -> i.group(
            MEAL_CODEC.listOf().optionalFieldOf("meals", List.of()).forGetter(s -> s.meals().active()),
            Codec.intRange(0, Recovery.MAX_NOURISH).optionalFieldOf("nourish", 0).forGetter(FoodState::nourish),
            Identifier.CODEC.listOf().optionalFieldOf("tasted", List.of()).forGetter(s -> new ArrayList<>(s.tasted())),
            Codec.intRange(0, 16).optionalFieldOf("bonus_slots", 0).forGetter(FoodState::bonusSlots),
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("helpings", Map.of()).forGetter(FoodState::helpings)
    ).apply(i, (meals, nourish, tasted, bonus, helpings) -> new FoodState(new Meals<>(meals), nourish, new LinkedHashSet<>(tasted), bonus, helpings)));

    public static final StreamCodec<ByteBuf, FoodState> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public FoodState {
        tasted = Collections.unmodifiableSet(new LinkedHashSet<>(tasted.size() > MAX_TASTED ? List.copyOf(tasted).subList(0, MAX_TASTED) : tasted));
        helpings = Map.copyOf(helpings);
    }

    /** Meal slots available now: the starting slots plus those unlocked, capped by the config. */
    public int slots() {
        return Math.clamp(FullCourseConfig.startingMealSlots() + bonusSlots, 1, FullCourseConfig.maxMealSlots());
    }

    public FoodState withMeals(Meals<Identifier> next) {
        return next == meals ? this : new FoodState(next, nourish, tasted, bonusSlots, helpings);
    }

    public FoodState withNourish(int next) {
        return next == nourish ? this : new FoodState(meals, next, tasted, bonusSlots, helpings);
    }

    public FoodState withTasted(Identifier dish) {
        if (tasted.contains(dish)) return this;
        Set<Identifier> next = new LinkedHashSet<>(tasted);
        next.add(dish);
        return new FoodState(meals, nourish, next, bonusSlots, helpings);
    }

    public FoodState withBonusSlots(int next) {
        return next == bonusSlots ? this : new FoodState(meals, nourish, tasted, next, helpings);
    }

    /** {@code dish} started a meal at {@code time}; times {@code window} or more before it are dropped. */
    public FoodState withHelping(Identifier dish, long time, long window) {
        Map<Identifier, Long> next = new HashMap<>();
        helpings.forEach((d, t) -> {
            if (time - t < window && t <= time) next.put(d, t);
        });
        next.put(dish, time);
        return new FoodState(meals, nourish, tasted, bonusSlots, next);
    }
}
