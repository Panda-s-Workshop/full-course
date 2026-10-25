package com.panda.fullcourse.checks;

import com.mojang.authlib.GameProfile;
import com.panda.fullcourse.food.Benefits;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.food.HealthMemory;
import com.panda.fullcourse.food.Profile;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.logic.Meals;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.registry.ModAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The food rules checked on a real server, shared by both loaders: NeoForge runs each
 * as a GameTest, Fabric runs them all in a dev client.
 * <p>
 * Each check runs inside one server tick: it drives {@link FoodRules#tick(ServerPlayer, long)} and
 * {@link FoodRules#onEatDish} at explicit game times far ahead of the clock, so the player's own ticks can't
 * interleave, and anything global it changes (difficulty, a gamerule) is put back before the tick ends.
 */
public final class RuleChecks {
    /** A failed check, with what was expected and what happened. */
    public static final class CheckFailure extends RuntimeException {
        CheckFailure(String message) {
            super(message);
        }
    }

    /** The player, a base time {@code t} (a multiple of 20, far ahead of the clock), and a free block to place on. */
    public record Ctx(ServerPlayer player, long t, BlockPos spot) {
        public MinecraftServer server() {
            return player.level().getServer();
        }

        public ServerLevel level() {
            return player.level();
        }
    }

    public static final Map<String, Consumer<Ctx>> CHECKS = new LinkedHashMap<>();

    static {
        CHECKS.put("hunger_pinned_after_sprint", RuleChecks::hungerPinned);
        CHECKS.put("eating_fills_nothing", RuleChecks::eatingFillsNothing);
        CHECKS.put("can_eat_when_full", RuleChecks::canEatWhenFull);
        CHECKS.put("recovery_after_delay", RuleChecks::recoveryAfterDelay);
        CHECKS.put("recovery_reset_by_damage", RuleChecks::recoveryResetByDamage);
        CHECKS.put("recovery_respects_gamerule", RuleChecks::recoveryRespectsGamerule);
        CHECKS.put("recovery_off_on_peaceful", RuleChecks::recoveryOffOnPeaceful);
        CHECKS.put("recovery_holds_under_tick_freeze", RuleChecks::recoveryHoldsUnderTickFreeze);
        CHECKS.put("food_respects_gamerule", RuleChecks::foodRespectsGamerule);
        CHECKS.put("candle_cake_refusal_drops_nothing", RuleChecks::candleCakeRefusalDropsNothing);
        CHECKS.put("mending_shortens_recovery", RuleChecks::mendingShortensRecovery);
        CHECKS.put("recovery_rate_attribute", RuleChecks::recoveryRateAttribute);
        CHECKS.put("steak_heals_eight_over_eight_seconds", RuleChecks::steakHeals);
        CHECKS.put("meal_fills_slot", RuleChecks::mealFillsSlot);
        CHECKS.put("meal_refresh", RuleChecks::mealRefresh);
        CHECKS.put("meal_replace_least_time", RuleChecks::mealReplaceLeastTime);
        CHECKS.put("family_strongest_wins", RuleChecks::familyStrongestWins);
        CHECKS.put("meal_survives_milk", RuleChecks::mealSurvivesMilk);
        CHECKS.put("meals_lost_on_death", RuleChecks::mealsLostOnDeath);
        CHECKS.put("relog_keeps_vigor_hearts", RuleChecks::relogKeepsVigorHearts);
        CHECKS.put("vanilla_tiers", RuleChecks::vanillaTiers);
        CHECKS.put("benefit_attribute_applied", RuleChecks::benefitApplied);
        CHECKS.put("benefit_removed_on_expiry", RuleChecks::benefitRemovedOnExpiry);
        CHECKS.put("balance_caps", RuleChecks::balanceCaps);
        CHECKS.put("saturation_heals", RuleChecks::saturationHeals);
        CHECKS.put("saturation_short_effect_heals", RuleChecks::saturationShortEffect);
        CHECKS.put("queasy_pauses_recovery", RuleChecks::queasyPausesRecovery);
        CHECKS.put("queasy_blocks_benefit", RuleChecks::queasyBlocksBenefit);
        CHECKS.put("golden_apple_is_remedy", RuleChecks::goldenAppleIsRemedy);
        CHECKS.put("cake_slice_gives_meal", RuleChecks::cakeSliceGivesMeal);
        CHECKS.put("cake_one_helping", RuleChecks::cakeOneHelping);
        CHECKS.put("tooltip_lines", RuleChecks::tooltipLines);
        KitchenChecks.register(CHECKS);
        ConfigChecks.register(CHECKS);
    }

    private RuleChecks() {}

    /**
     * Runs one check on {@code player} (a fresh survival player is best; a real one works, its food state is reset
     * before and after). Throws {@link CheckFailure}.
     */
    public static void run(String name, ServerPlayer player, BlockPos spot) {
        Consumer<Ctx> check = CHECKS.get(name);
        if (check == null) throw new IllegalArgumentException("no rule check " + name);
        MinecraftServer server = player.level().getServer();
        Difficulty difficulty = player.level().getDifficulty();
        long t = (player.level().getGameTime() / 20 + 50_000) * 20;
        reset(player);
        server.setDifficulty(Difficulty.NORMAL, true);
        try {
            check.accept(new Ctx(player, t, spot));
        } finally {
            server.setDifficulty(difficulty, true);
            reset(player);
        }
    }

    private static void reset(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setSprinting(false);
        player.removeAllEffects();
        Services.PLATFORM.setFoodState(player, FoodState.EMPTY);
        Benefits.apply(player, Map.of());
        ((HealthMemory) player).fullcourse$setSavedHealth(0F);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5F);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    static FoodState state(Ctx c) {
        return FoodRules.state(c.player());
    }

    static void eat(Ctx c, Item item, long at) {
        ItemStack stack = new ItemStack(item);
        FoodRules.onEat(c.player(), stack, stack.get(DataComponents.FOOD), at);
    }

    /** Eats through the real path: the item's consume effects, with the {@code FoodProperties.onConsume} mixin. */
    private static void eatForReal(Ctx c, Item item) {
        new ItemStack(item).finishUsingItem(c.level(), c.player());
    }

    static Profile profile(Ctx c, Item item) {
        return FoodProfiles.get(c.server(), item);
    }

    static List<Identifier> dishes(Ctx c) {
        return state(c).meals().active().stream().map(Meals.Meal::dish).toList();
    }

    static Identifier id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    private static List<String> text(Ctx c, Item item, boolean shift, boolean tasted) {
        var food = new ItemStack(item).get(DataComponents.FOOD);
        return FoodText.lines(profile(c, item), food == null ? 0 : food.nutrition(), shift, tasted).stream().map(Component::getString).toList();
    }

    /** Down to 10 HP, hit at time {@code at} as far as Recovery can tell. */
    static void hurtAt(Ctx c, long at) {
        c.player().setHealth(10F);
        c.player().lastDamageStamp = at;
    }

    static void ticks(Ctx c, long from, long toInclusive) {
        for (long now = from; now <= toInclusive; now++) FoodRules.tick(c.player(), now);
    }

    static void check(boolean ok, String fmt, Object... args) {
        if (!ok) throw new CheckFailure(String.format(fmt, args));
    }

    static void health(Ctx c, float expected, String when) {
        float hp = c.player().getHealth();
        check(Math.abs(hp - expected) < 0.01F, "%s: health %s, expected %s", when, hp, expected);
    }

    private static boolean hasModifier(Ctx c, Holder<Attribute> attribute, Family family) {
        return c.player().getAttribute(attribute).hasModifier(Benefits.modifierId(family, 0));
    }

    // ---- 3.1 hunger removed -------------------------------------------------------------------------------------

    /** 30 s of sprint-jumping: the food bar never moves and nothing starves. */
    private static void hungerPinned(Ctx c) {
        var food = c.player().getFoodData();
        c.player().setSprinting(true);
        for (int i = 0; i < 600; i++) {
            food.addExhaustion(0.1F + (i % 10 == 0 ? 0.2F : 0F)); // sprinting, and a sprint-jump every half second
            food.tick(c.player());
        }
        check(food.getFoodLevel() == 20, "food level %s after 30 s of sprinting, expected 20", food.getFoodLevel());
        check(food.getSaturationLevel() == 5F, "saturation %s, expected 5", food.getSaturationLevel());
        check(food.hasEnoughFood(), "enough food to sprint");
        health(c, 20F, "after 30 s of sprinting");
    }

    /** {@code FoodData.eat} is a no-op: food heals over time instead. */
    private static void eatingFillsNothing(Ctx c) {
        var food = c.player().getFoodData();
        food.setFoodLevel(10);
        food.eat(6, 1.2F);
        check(food.getFoodLevel() == 10, "FoodData.eat filled the bar to %s", food.getFoodLevel());
    }

    private static void canEatWhenFull(Ctx c) {
        check(c.player().getFoodData().getFoodLevel() == 20, "starts full");
        check(c.player().canEat(false), "a full player can still eat");
    }

    // ---- 3.2 Recovery -------------------------------------------------------------------------------------------

    private static void recoveryAfterDelay(Ctx c) {
        long t = c.t();
        hurtAt(c, t);
        ticks(c, t + 1, t + 159);
        health(c, 10F, "just before 8 s");
        FoodRules.tick(c.player(), t + 160);
        health(c, 11F, "8 s after the hit");
        ticks(c, t + 161, t + 239);
        health(c, 11F, "one interval is 4 s");
        FoodRules.tick(c.player(), t + 240);
        health(c, 12F, "12 s after the hit");
    }

    /** A real hit (not a set timestamp) restarts the delay. */
    private static void recoveryResetByDamage(Ctx c) {
        ServerPlayer p = c.player();
        // a mock player's client never says it loaded, and until then the server ignores hits
        if (!p.connection.hasClientLoaded()) p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        p.setHealth(16F);
        long now = c.level().getGameTime();
        boolean hurt = p.hurtServer(c.level(), p.damageSources().fellOutOfWorld(), 2F);
        check(hurt, "the hit didn't land");
        check(p.lastDamageStamp == now, "the hit stamped %s, expected the game time %s", p.lastDamageStamp, now);
        float hp = p.getHealth();
        ticks(c, now + 1, now + 159);
        health(c, hp, "no Recovery during the 8 s after a real hit");
        FoodRules.tick(p, now + 160);
        health(c, hp + 1F, "Recovery 8 s after a real hit");
    }

    private static void recoveryRespectsGamerule(Ctx c) {
        long t = c.t();
        hurtAt(c, t);
        GameRules rules = c.level().getGameRules();
        boolean before = rules.get(GameRules.NATURAL_HEALTH_REGENERATION);
        rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, c.server());
        try {
            FoodRules.tick(c.player(), t + 160);
        } finally {
            rules.set(GameRules.NATURAL_HEALTH_REGENERATION, before, c.server());
        }
        health(c, 10F, "naturalHealthRegeneration off");
    }

    /** With naturalHealthRegeneration off food heals nothing, as in vanilla: UHC packs heal only by their own means. */
    private static void foodRespectsGamerule(Ctx c) {
        long t = c.t();
        c.player().setHealth(10F);
        c.player().lastDamageStamp = t - 10_000;
        GameRules rules = c.level().getGameRules();
        boolean before = rules.get(GameRules.NATURAL_HEALTH_REGENERATION);
        rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, c.server());
        try {
            FoodRules.onEatDish(c.player(), Items.COOKED_BEEF, 8, t);
            c.player().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, 40, 1));
            ticks(c, t + 1, t + 200);
        } finally {
            rules.set(GameRules.NATURAL_HEALTH_REGENERATION, before, c.server());
            c.player().removeEffect(net.minecraft.world.effect.MobEffects.SATURATION);
        }
        health(c, 10F, "food and Saturation with naturalHealthRegeneration off");
    }

    private static void recoveryOffOnPeaceful(Ctx c) {
        long t = c.t();
        hurtAt(c, t);
        c.server().setDifficulty(Difficulty.PEACEFUL, true);
        FoodRules.tick(c.player(), t + 160);
        health(c, 10F, "Peaceful heals its own way");
    }

    /** {@code /tick freeze} stops the game clock while players keep ticking: nothing heals until it thaws. */
    private static void recoveryHoldsUnderTickFreeze(Ctx c) {
        long t = c.t();
        hurtAt(c, t);
        c.server().tickRateManager().setFrozen(true);
        try {
            ticks(c, t + 160, t + 400);
        } finally {
            c.server().tickRateManager().setFrozen(false);
        }
        health(c, 10F, "frozen clock");
        FoodRules.tick(c.player(), t + 400);
        health(c, 11F, "thawed");
    }

    /** Rabbit stew is a soup: Mending II halves the interval to 40 ticks. */
    private static void mendingShortensRecovery(Ctx c) {
        long t = c.t();
        eat(c, Items.RABBIT_STEW, t);
        Services.PLATFORM.setFoodState(c.player(), state(c).withNourish(0)); // only Recovery heals here
        hurtAt(c, t);
        FoodRules.tick(c.player(), t + 160);
        health(c, 11F, "first heal after the delay");
        ticks(c, t + 161, t + 199);
        health(c, 11F, "before 40 ticks");
        FoodRules.tick(c.player(), t + 200);
        health(c, 12F, "Mending II heals every 40 ticks");
    }

    /**
     * Players have {@code fullcourse:recovery_rate} at 1; Mending II raises it to 2; another mod's modifier on it
     * speeds Recovery up the same way, and one that takes it to 0 stops Recovery.
     */
    private static void recoveryRateAttribute(Ctx c) {
        AttributeInstance rate = c.player().getAttribute(ModAttributes.RECOVERY_RATE.holder());
        check(rate != null, "players have no fullcourse:recovery_rate");
        check(rate.getBaseValue() == 1, "recovery_rate starts at %s, expected 1", rate.getBaseValue());
        long t = c.t();
        eat(c, Items.RABBIT_STEW, t);
        check(rate.getValue() == 2, "Mending II puts recovery_rate at %s, expected 2", rate.getValue());
        Services.PLATFORM.setFoodState(c.player(), FoodState.EMPTY);
        Benefits.apply(c.player(), Map.of());
        check(rate.getValue() == 1, "with no meal recovery_rate is %s, expected 1", rate.getValue());
        Identifier other = Identifier.fromNamespaceAndPath("othermod", "faster_recovery");
        try {
            rate.addOrUpdateTransientModifier(new AttributeModifier(other, 1, AttributeModifier.Operation.ADD_VALUE));
            hurtAt(c, t);
            FoodRules.tick(c.player(), t + 160);
            ticks(c, t + 161, t + 199);
            health(c, 11F, "another mod's +1: the first heal, then nothing before 40 ticks");
            FoodRules.tick(c.player(), t + 200);
            health(c, 12F, "another mod's +1 heals every 40 ticks");
            rate.addOrUpdateTransientModifier(new AttributeModifier(other, -1, AttributeModifier.Operation.ADD_VALUE));
            hurtAt(c, t);
            ticks(c, t + 160, t + 400);
            health(c, 10F, "recovery_rate 0 stops Recovery");
        } finally {
            rate.removeModifier(other);
        }
    }

    // ---- 3.3 eating heals ---------------------------------------------------------------------------------------

    private static void steakHeals(Ctx c) {
        long t = c.t();
        eatForReal(c, Items.COOKED_BEEF);
        check(state(c).nourish() == 8, "steak leaves %s HP to heal, expected 8", state(c).nourish());
        c.player().setHealth(4F);
        c.player().lastDamageStamp = t + 1000; // keeps Recovery out of the way
        ticks(c, t + 1, t + 160);
        health(c, 12F, "8 s after eating steak");
        ticks(c, t + 161, t + 400);
        health(c, 12F, "steak heals 8, no more");
        check(state(c).nourish() == 0, "the heal is used up");
    }

    // ---- 3.5 meal slots -----------------------------------------------------------------------------------------

    private static void mealFillsSlot(Ctx c) {
        eatForReal(c, Items.COOKED_BEEF);
        FoodState s = state(c);
        check(dishes(c).equals(List.of(id(Items.COOKED_BEEF))), "meals %s, expected [cooked_beef]", dishes(c));
        check(s.meals().active().getFirst().tier() == Tier.SIMPLE, "steak is a Simple meal");
        check(s.tasted().contains(id(Items.COOKED_BEEF)), "steak counts as tasted");
        check(s.slots() == 2, "two slots to start, got %s", s.slots());
    }

    private static void mealRefresh(Ctx c) {
        long t = c.t();
        eat(c, Items.BREAD, t);
        eat(c, Items.BREAD, t + 500);
        check(dishes(c).equals(List.of(id(Items.BREAD))), "eating it again takes no new slot: %s", dishes(c));
        long end = state(c).meals().active().getFirst().end();
        check(end == t + 500 + Tier.SIMPLE.durationTicks(1.0), "the refresh restarts the time: ends %s", end);
    }

    private static void mealReplaceLeastTime(Ctx c) {
        long t = c.t();
        eat(c, Items.COOKED_BEEF, t);
        eat(c, Items.BREAD, t + 100);
        eat(c, Items.BAKED_POTATO, t + 200);
        check(dishes(c).equals(List.of(id(Items.BAKED_POTATO), id(Items.BREAD))),
                "a full row replaces the meal with the least time left (steak): %s", dishes(c));
    }

    /** Vigor from a Hearty stew (II) and a Prepared pie (I): II wins, they don't add up. */
    private static void familyStrongestWins(Ctx c) {
        eat(c, Items.PUMPKIN_PIE, c.t());
        modifier(c, Attributes.MAX_HEALTH, Family.VIGOR, 2.0);
        eat(c, Items.RABBIT_STEW, c.t());
        modifier(c, Attributes.MAX_HEALTH, Family.VIGOR, 4.0);
        check(c.player().getMaxHealth() == 24F, "max health %s, expected 24", c.player().getMaxHealth());
    }

    private static void mealSurvivesMilk(Ctx c) {
        eat(c, Items.RABBIT_STEW, c.level().getGameTime());
        eatForReal(c, Items.MILK_BUCKET);
        check(dishes(c).equals(List.of(id(Items.RABBIT_STEW))), "milk left the meal alone: %s", dishes(c));
        check(hasModifier(c, Attributes.MAX_HEALTH, Family.VIGOR), "milk left the benefit alone");
    }

    private static void mealsLostOnDeath(Ctx c) {
        eat(c, Items.COOKED_BEEF, c.t());
        FoodRules.onDeath(c.player());
        check(state(c).meals().isEmpty(), "meals lost on death");
        check(state(c).tasted().contains(id(Items.COOKED_BEEF)), "the tasted set stays");
    }

    /** Saved at 24 of 24 with Vigor II, loaded into a new player: 24 of 24 again once the meal is back. */
    private static void relogKeepsVigorHearts(Ctx c) {
        eat(c, Items.RABBIT_STEW, c.level().getGameTime());
        c.player().setHealth(24F);
        health(c, 24F, "before saving");
        var registries = c.server().registryAccess();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        c.player().saveWithoutId(out);
        CompoundTag saved = out.buildResult();
        ServerPlayer loaded = new ServerPlayer(c.server(), c.level(), new GameProfile(UUID.randomUUID(), "fc_relog"), ClientInformation.createDefault());
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        check(dishes(new Ctx(loaded, c.t(), c.spot())).equals(List.of(id(Items.RABBIT_STEW))), "the meal was saved with the player");
        FoodRules.tick(loaded, c.level().getGameTime());
        check(loaded.getMaxHealth() == 24F, "max health after loading %s, expected 24", loaded.getMaxHealth());
        check(Math.abs(loaded.getHealth() - 24F) < 0.01F, "health after loading %s, expected 24", loaded.getHealth());
    }

    // ---- 3.4 tiers of vanilla food ------------------------------------------------------------------------------

    private static void vanillaTiers(Ctx c) {
        tier(c, Items.BEEF, Tier.RAW);
        tier(c, Items.APPLE, Tier.RAW);
        tier(c, Items.COOKED_BEEF, Tier.SIMPLE);
        tier(c, Items.BREAD, Tier.SIMPLE);
        tier(c, Items.COOKIE, Tier.SIMPLE);
        tier(c, Items.MUSHROOM_STEW, Tier.SIMPLE);
        tier(c, Items.BEETROOT_SOUP, Tier.SIMPLE);
        tier(c, Items.PUMPKIN_PIE, Tier.PREPARED);
        tier(c, Items.RABBIT_STEW, Tier.HEARTY);
        tier(c, Items.CAKE, Tier.HEARTY);
        families(c, Items.RABBIT_STEW, List.of(Family.VIGOR, Family.MENDING)); // a soup always mends
        families(c, Items.MUSHROOM_STEW, List.of(Family.MENDING));
        families(c, Items.PUMPKIN_PIE, List.of(Family.VIGOR, Family.FORTUNE));
        families(c, Items.CAKE, List.of(Family.FORTUNE, Family.DILIGENCE)); // cake's profile override
    }

    private static void families(Ctx c, Item item, List<Family> expected) {
        List<Family> got = profile(c, item).families();
        check(got.equals(expected), "%s families %s, expected %s", id(item), got, expected);
    }

    private static void tier(Ctx c, Item item, Tier expected) {
        Profile p = profile(c, item);
        check(p.tier() == expected, "%s is %s (score %s), expected %s", id(item), p.tier(), p.score(), expected);
    }

    // ---- 3.6 benefits, 5 balance --------------------------------------------------------------------------------

    private static void benefitApplied(Ctx c) {
        eat(c, Items.PUMPKIN_PIE, c.t()); // Prepared: Vigor I, Fortune I
        modifier(c, Attributes.MAX_HEALTH, Family.VIGOR, 2.0);
        modifier(c, Attributes.LUCK, Family.FORTUNE, 1.0);
    }

    private static void modifier(Ctx c, Holder<Attribute> attribute, Family family, double amount) {
        AttributeModifier m = c.player().getAttribute(attribute).getModifier(Benefits.modifierId(family, 0));
        check(m != null, "%s has no %s modifier", attribute.getRegisteredName(), family);
        check(m.amount() == amount, "%s modifier %s, expected %s", family, m.amount(), amount);
    }

    private static void benefitRemovedOnExpiry(Ctx c) {
        long t = c.t();
        eat(c, Items.PUMPKIN_PIE, t);
        long end = state(c).meals().active().getFirst().end();
        check(end == t + Tier.PREPARED.durationTicks(1.0), "Prepared lasts 5 minutes");
        FoodRules.tick(c.player(), end - 1);
        check(!state(c).meals().isEmpty(), "still active a tick before the end");
        FoodRules.tick(c.player(), end);
        check(state(c).meals().isEmpty(), "gone at the end");
        check(!hasModifier(c, Attributes.MAX_HEALTH, Family.VIGOR), "Vigor removed");
        check(!hasModifier(c, Attributes.LUCK, Family.FORTUNE), "Fortune removed");
        check(c.player().getMaxHealth() == 20F, "max health back to 20, got %s", c.player().getMaxHealth());
    }

    /** A Gourmet family (III) adds at most +3 damage; Vigor IV, the most there is, at most +8 HP. */
    private static void balanceCaps(Ctx c) {
        double brawn = Benefits.EFFECTS.get(Family.BRAWN).getFirst().perLevel() * Tier.GOURMET.level();
        check(brawn <= 3.0, "Brawn III adds %s damage, the cap is 3 (Strength I)", brawn);
        double vigor = Benefits.EFFECTS.get(Family.VIGOR).getFirst().perLevel() * Tier.FEAST.level();
        check(vigor <= 8.0, "Vigor IV adds %s HP, the cap is 8", vigor);
    }

    // ---- 3.9 vanilla effects ------------------------------------------------------------------------------------

    private static void saturationHeals(Ctx c) {
        long t = c.t();
        hurtAt(c, t);
        c.player().addEffect(new MobEffectInstance(MobEffects.SATURATION, 100, 1));
        FoodRules.tick(c.player(), t + 1);
        health(c, 12F, "Saturation II heals 2 on its first tick");
        ticks(c, t + 2, t + 20);
        health(c, 12F, "and not again within the second");
        FoodRules.tick(c.player(), t + 21);
        health(c, 14F, "then 2 a second");
    }

    /** Suspicious stew's Saturation lasts 7 ticks: it still heals once. */
    private static void saturationShortEffect(Ctx c) {
        long t = c.t();
        hurtAt(c, t);
        c.player().addEffect(new MobEffectInstance(MobEffects.SATURATION, 7, 0));
        for (int i = 1; i <= 7; i++) FoodRules.tick(c.player(), t + 6 + i); // starting off a second boundary
        health(c, 11F, "a 7-tick Saturation I heals 1");
    }

    private static void queasyPausesRecovery(Ctx c) {
        long t = c.t();
        hurtAt(c, t);
        c.player().addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0));
        FoodRules.tick(c.player(), t + 160);
        health(c, 10F, "Queasy pauses Recovery");
    }

    private static void queasyBlocksBenefit(Ctx c) {
        c.player().addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0));
        eat(c, Items.RABBIT_STEW, c.t());
        check(state(c).meals().isEmpty(), "no meal while Queasy");
        check(state(c).nourish() == 10, "the food still heals, nourish %s", state(c).nourish());
    }

    private static void goldenAppleIsRemedy(Ctx c) {
        check(profile(c, Items.GOLDEN_APPLE).remedy(), "golden apple is a remedy");
        check(profile(c, Items.ENCHANTED_GOLDEN_APPLE).remedy(), "enchanted golden apple is a remedy");
        check(profile(c, Items.GOLDEN_APPLE).tier() == Tier.RAW, "a remedy has no tier, got %s", profile(c, Items.GOLDEN_APPLE).tier());
        eat(c, Items.GOLDEN_APPLE, c.t());
        check(state(c).meals().isEmpty(), "a remedy takes no meal slot");
    }

    // ---- 3.8 cake -----------------------------------------------------------------------------------------------

    /** A slice through the real block: a Hearty meal (Fortune + Diligence II) and 2 HP to heal. */
    private static void cakeSliceGivesMeal(Ctx c) {
        BlockPos pos = c.spot();
        BlockState before = c.level().getBlockState(pos);
        c.level().setBlock(pos, Blocks.CAKE.defaultBlockState(), 3);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            c.level().getBlockState(pos).useWithoutItem(c.level(), c.player(), hit);
            check(dishes(c).equals(List.of(id(Items.CAKE))), "a slice gives a cake meal: %s", dishes(c));
            check(state(c).meals().active().getFirst().tier() == Tier.HEARTY, "cake is Hearty");
            check(state(c).nourish() == 2, "a slice heals 2, nourish %s", state(c).nourish());
            check(c.level().getBlockState(pos).is(Blocks.CAKE), "the cake is still there, one slice less");
        } finally {
            c.level().setBlock(pos, before, 3);
        }
    }

    /** Holding the use key on a cake takes one slice, not the cake: the second right-click is refused. */
    private static void cakeOneHelping(Ctx c) {
        BlockPos pos = c.spot();
        BlockState before = c.level().getBlockState(pos);
        c.level().setBlock(pos, Blocks.CAKE.defaultBlockState(), 3);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            c.level().getBlockState(pos).useWithoutItem(c.level(), c.player(), hit);
            c.level().getBlockState(pos).useWithoutItem(c.level(), c.player(), hit);
            c.level().getBlockState(pos).useWithoutItem(c.level(), c.player(), hit);
            int bites = c.level().getBlockState(pos).getValue(net.minecraft.world.level.block.CakeBlock.BITES);
            check(bites == 1, "three right-clicks take one slice, bites %s", bites);
            check(state(c).meals().active().size() == 1, "one cake meal: %s", dishes(c));
        } finally {
            c.level().setBlock(pos, before, 3);
        }
    }

    /** A refused helping from a candle cake leaves the candle on it: no candle for every click. */
    private static void candleCakeRefusalDropsNothing(Ctx c) {
        BlockPos pos = c.spot();
        BlockState before = c.level().getBlockState(pos);
        FoodRules.onEatDish(c.player(), Items.CAKE, FoodRules.CAKE_SLICE_NUTRITION, c.level().getGameTime());
        c.level().setBlock(pos, Blocks.CANDLE_CAKE.defaultBlockState(), 3);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            for (int i = 0; i < 3; i++) c.level().getBlockState(pos).useWithoutItem(c.level(), c.player(), hit);
            long candles = c.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2))
                    .stream().filter(e -> e.getItem().is(Items.CANDLE)).mapToInt(e -> e.getItem().getCount()).sum();
            check(candles == 0, "a refused candle cake dropped %s candles", candles);
            check(c.level().getBlockState(pos).is(Blocks.CANDLE_CAKE), "the refused candle cake should stay whole: %s", c.level().getBlockState(pos));
        } finally {
            c.level().setBlock(pos, before, 3);
            c.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2)).forEach(e -> e.discard());
        }
    }

    // ---- 3.12 tooltip -------------------------------------------------------------------------------------------

    private static void tooltipLines(Ctx c) {
        List<String> stew = text(c, Items.RABBIT_STEW, false, false);
        check(stew.get(0).contains("Hearty") && !stew.get(0).contains("✔"), "stars line %s", stew.get(0));
        check(stew.get(1).equals("Vigor II: +4 max health"), "first benefit %s", stew.get(1));
        check(stew.get(2).equals("Mending II: +100% Recovery"), "second benefit %s", stew.get(2));
        check(stew.get(3).equals("Lasts 10:00 · Heals 5 ❤ over 10 s"), "duration and heal %s", stew.get(3));
        check(stew.get(4).startsWith("Shift"), "shift hint %s", stew);
        List<String> shift = text(c, Items.RABBIT_STEW, true, true);
        check(shift.get(0).endsWith("✔"), "check mark once tasted: %s", shift.get(0));
        check(shift.contains("Score 5 · Gourmet at 8"), "score line %s", shift);
        List<String> steak = text(c, Items.COOKED_BEEF, true, false);
        check(steak.contains("1 step · 1 station · 1 ingredient"), "a one-step chain reads in the singular: %s", steak);
        // 0.10 a level times 3 is 0.30000000000000004 in floating point
        String diligence = FoodText.benefit(Family.DILIGENCE, 3).getString();
        check(diligence.equals("Diligence III: +30% mining speed"), "whole percentages read without a decimal: %s", diligence);
        List<String> raw = text(c, Items.BEEF, false, false);
        check(raw.get(1).contains("Made into a dish, it gives a meal") && raw.get(2).equals("Heals 1.5 ❤ over 3 s"), "raw lines %s", raw);
        List<String> remedy = text(c, Items.GOLDEN_APPLE, false, false);
        check(remedy.size() == 2 && remedy.get(0).startsWith("Remedy") && remedy.get(1).equals("Heals 2 ❤ over 4 s"),
                "a remedy shows its line and its heal, no stars: %s", remedy);
    }
}
