package com.panda.fullcourse.food;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.kitchen.FeastBlock;
import com.panda.fullcourse.logic.Meals;
import com.panda.fullcourse.logic.Milestones;
import com.panda.fullcourse.logic.Recovery;
import com.panda.fullcourse.network.RecoveryPayload;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.platform.registry.DeferredHolder;
import com.panda.fullcourse.registry.ModAttributes;
import com.panda.fullcourse.registry.ModSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The server's food rules: what eating does, what each tick does and what death
 * does. Starting a meal and a meal running out each play a cue to that player. Loaders call {@link #tick} after every player tick and {@link #onDeath} when a player dies; eating comes from
 * common mixins on {@code FoodProperties.onConsume} and the cake block.
 */
public final class FoodRules {
    /** Vanilla's cake slice: 2 nutrition. */
    public static final int CAKE_SLICE_NUTRITION = 2;
    /** How often a player's slots are counted again outside meals (after a login or a datapack reload). */
    private static final int SLOT_CHECK_TICKS = 100;
    /** Each player's current Saturation effect and its ticks so far: it heals on the first and then every second. */
    private static final Map<ServerPlayer, SaturationRun> SATURATION = new WeakHashMap<>();

    private record SaturationRun(MobEffectInstance effect, int ticks) {}

    private FoodRules() {}

    public static FoodState state(ServerPlayer player) {
        return Services.PLATFORM.foodState(player);
    }

    private static void set(ServerPlayer player, FoodState before, FoodState after) {
        if (after != before) Services.PLATFORM.setFoodState(player, after);
    }

    /** A player ate food: start its heal and, for a meal, fill a slot. Queasy (the Hunger effect) blocks the meal. */
    public static void onEat(ServerPlayer player, ItemStack stack, FoodProperties food) {
        onEat(player, stack, food, player.level().getGameTime());
    }

    /** Eating at game time {@code now}. */
    public static void onEat(ServerPlayer player, ItemStack stack, FoodProperties food, long now) {
        onEatDish(player, stack.getItem(), food.nutrition(), now);
    }

    public static void onEatDish(ServerPlayer player, Item dish, int nutrition) {
        onEatDish(player, dish, nutrition, player.level().getGameTime());
    }

    /** {@code dish} eaten at {@code now}, healing {@code nutrition} HP over as many seconds. */
    public static void onEatDish(ServerPlayer player, Item dish, int nutrition, long now) {
        FoodState before = state(player);
        FoodState s = before;
        if (!FullCourseConfig.keepVanillaHunger()) s = s.withNourish(Recovery.nourish(s.nourish(), nutrition));
        Profile profile = FoodProfiles.get(player.level().getServer(), dish);
        if (profile.givesMeal()) {
            Component name = dish.getName(new ItemStack(dish));
            if (player.hasEffect(MobEffects.HUNGER)) {
                player.sendSystemMessage(Component.translatable("message.fullcourse.queasy", name).withStyle(ChatFormatting.GREEN), true);
            } else {
                // a slice is its cake: one tasted dish, one meal
                Identifier id = BuiltInRegistries.ITEM.getKey(FoodProfiles.tastedAs(player.level().getServer(), dish));
                int duration = profile.tier().durationTicks(FullCourseConfig.durationMultiplier());
                Meals.Meal<Identifier> meal = new Meals.Meal<>(id, profile.tier(), profile.families(), profile.tier().level(), now, now + duration);
                s = unlockSlots(player, s.withTasted(id).withHelping(id, now, FeastBlock.HELPING_TICKS));
                Meals.Target target = s.meals().target(id, s.slots());
                Component replaced = target.fill() == Meals.Fill.REPLACE ? itemName(s.meals().active().get(target.slot()).dish()) : null;
                s = s.withMeals(s.meals().eat(meal, s.slots()));
                player.sendSystemMessage(FoodText.mealMessage(name, profile, replaced), true);
                cue(player, ModSounds.MEAL_START);
                FoodAdvancements.onMeal(player, profile.tier(), s.tasted());
            }
        } else if (!FullCourseConfig.keepVanillaHunger()) {
            // with no food bar to watch, say why nothing filled a slot
            Component name = dish.getName(new ItemStack(dish));
            player.sendSystemMessage(Component.translatable(profile.remedy() ? "message.fullcourse.no_meal.remedy" : "message.fullcourse.no_meal.raw", name)
                    .withStyle(ChatFormatting.GRAY), true);
        }
        set(player, before, s);
        Benefits.apply(player, s.meals().strongest());
    }

    /**
     * The slots the tasted set has earned ({@link Milestones}), each with its advancement when it really adds a slot (the
     * config's maximum may stop short of the fifth). Slots given another way (a command) are kept, so this only ever
     * adds. The new slot is there for the meal that earned it. Also run every few seconds, so a login or a datapack
     * that rates dishes higher catches up.
     */
    private static FoodState unlockSlots(ServerPlayer player, FoodState s) {
        int reached = Milestones.reached(Tasting.count(s.tasted(), item -> FoodProfiles.get(player.level().getServer(), item)),
                FullCourseConfig.slotMilestones());
        if (reached <= s.bonusSlots()) return s;
        for (int k = s.bonusSlots(); k < reached; k++) {
            if (FullCourseConfig.startingMealSlots() + k + 1 <= FullCourseConfig.maxMealSlots()) FoodAdvancements.award(player, "slot_" + (k + 3));
        }
        return s.withBonusSlots(reached);
    }

    /** After every server tick of a player. */
    public static void tick(ServerPlayer player) {
        tick(player, player.level().getGameTime());
    }

    /** One tick at game time {@code now} (rule checks step through time with it). */
    public static void tick(ServerPlayer player, long now) {
        if (!player.isAlive()) return;
        // under /tick freeze players keep ticking while the game time stands still; a /tick step runs the game,
        // its last tick included (the step count is spent before the tick runs)
        if (!player.level().tickRateManager().runsNormally()) return;
        restoreHealth(player);
        FoodState before = state(player);
        FoodState s = before.withMeals(before.meals().expire(now));
        // a meal of a food whose mod was removed ends: nothing can show it
        if (s.meals().active().stream().anyMatch(m -> !BuiltInRegistries.ITEM.containsKey(m.dish()))) {
            s = s.withMeals(new Meals<>(s.meals().active().stream().filter(m -> BuiltInRegistries.ITEM.containsKey(m.dish())).toList()));
        }
        if (s.meals().active().size() < before.meals().active().size()) cue(player, ModSounds.MEAL_END);
        if (now % SLOT_CHECK_TICKS == 0) s = unlockSlots(player, s);
        if (!FullCourseConfig.keepVanillaHunger()) {
            // naturalHealthRegeneration off (UHC packs): food heals nothing, as in vanilla
            boolean regenerates = player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
            if (now % Recovery.NOURISH_TICKS == 0 && s.nourish() > 0) {
                if (player.isHurt() && regenerates) player.heal(1F);
                s = s.withNourish(s.nourish() - 1);
            }
            if (regenerates) saturation(player);
            else SATURATION.remove(player);
            if (recovers(player)) {
                int interval = Recovery.interval(FullCourseConfig.recoveryIntervalTicks(), recoveryRate(player));
                if (Recovery.healsNow(now - player.lastDamageStamp, FullCourseConfig.recoveryDelayTicks(), interval)) {
                    player.heal(1F);
                    Services.PLATFORM.sendToPlayer(player, RecoveryPayload.INSTANCE);
                }
            }
        }
        set(player, before, s);
        if (now % 20 == 0 || s.meals() != before.meals()) Benefits.apply(player, s.meals().strongest());
    }

    private static Component itemName(Identifier dish) {
        Item item = BuiltInRegistries.ITEM.getValue(dish);
        return item.getName(new ItemStack(item));
    }

    /** Plays a meal cue to {@code player} alone: their meals are their own news, not the room's. */
    private static void cue(ServerPlayer player, DeferredHolder<SoundEvent, SoundEvent> sound) {
        player.connection.send(new ClientboundSoundPacket(sound.holder(), SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
                1F, 1F, player.getRandom().nextLong()));
    }

    /** {@code fullcourse:recovery_rate}, which Mending raises; the default pace if something removed the attribute. */
    private static double recoveryRate(ServerPlayer player) {
        AttributeInstance rate = player.getAttribute(ModAttributes.RECOVERY_RATE.holder());
        return rate == null ? 1 : rate.getValue();
    }

    /** After a login or an End exit: the meal bonus back first, then the health the save or the old player had. */
    private static void restoreHealth(ServerPlayer player) {
        if (!(player instanceof HealthMemory memory) || memory.fullcourse$savedHealth() <= 0) return;
        float saved = memory.fullcourse$savedHealth();
        memory.fullcourse$setSavedHealth(0F);
        Benefits.apply(player, state(player).meals().strongest());
        if (saved > player.getHealth()) player.setHealth(Math.min(saved, player.getMaxHealth()));
    }

    /** The Saturation effect heals (amplifier + 1) HP on its first tick and every second after. */
    private static void saturation(ServerPlayer player) {
        MobEffectInstance effect = player.getEffect(MobEffects.SATURATION);
        if (effect == null) {
            SATURATION.remove(player);
            return;
        }
        // a new effect instance (removed and given again, even within one tick) starts over
        SaturationRun run = SATURATION.get(player);
        run = run != null && run.effect() == effect ? new SaturationRun(effect, run.ticks() + 1) : new SaturationRun(effect, 1);
        SATURATION.put(player, run);
        if ((run.ticks() - 1) % 20 == 0 && player.isHurt()) player.heal(effect.getAmplifier() + 1F);
    }

    /** Recovery runs when hurt, not Queasy, on a difficulty above Peaceful (Peaceful heals its own way), with the gamerule on. */
    private static boolean recovers(ServerPlayer player) {
        return player.isHurt()
                && !player.hasEffect(MobEffects.HUNGER)
                && player.level().getDifficulty() != Difficulty.PEACEFUL
                && player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
    }

    /** A player died: meals are lost unless the config keeps them; the tasted set and unlocked slots stay. */
    public static void onDeath(ServerPlayer player) {
        SATURATION.remove(player);
        if (FullCourseConfig.keepMealsOnDeath()) return;
        FoodState before = state(player);
        set(player, before, before.withMeals(Meals.empty()).withNourish(0));
    }
}
