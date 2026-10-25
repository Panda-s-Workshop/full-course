package com.panda.fullcourse.food;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.logic.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The text a food shows: tier stars and name (a check mark once tasted), one line per benefit saying what
 * it does, how long it lasts and how much the food heals, "Remedy" or "Raw" where it applies, and with Shift the
 * recipe chain and the score that earned the tier. Common, so GameTests can read it; the client's tooltip hook inserts
 * it under the item's name.
 */
public final class FoodText {
    private FoodText() {}

    /**
     * @param nutrition the food's nutrition: it heals that many HP over that many seconds; 0 for none
     * @param tasted    the player has eaten this dish before
     */
    public static List<Component> lines(Profile p, int nutrition, boolean shift, boolean tasted) {
        return lines(p, nutrition, shift, tasted, FullCourseConfig.regenerates());
    }

    /**
     * {@link #lines(Profile, int, boolean, boolean)} for a world whose {@code naturalHealthRegeneration} is
     * {@code regenerates}: the server's own for a command, the synced one in a tooltip.
     */
    public static List<Component> lines(Profile p, int nutrition, boolean shift, boolean tasted, boolean regenerates) {
        List<Component> out = new ArrayList<>();
        // with vanilla hunger food fills the bar instead of healing over time, and Recovery (Mending's work) is off
        boolean hunger = FullCourseConfig.keepVanillaHunger();
        // with naturalHealthRegeneration off food heals nothing and Recovery doesn't run, as vanilla's food doesn't heal
        if (hunger || !regenerates) nutrition = 0;
        if (p.remedy()) { // no tier, so no stars
            out.add(Component.translatable("tooltip.fullcourse.remedy").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (nutrition > 0) out.add(heal(nutrition).withStyle(ChatFormatting.GRAY));
            return out;
        }
        Tier tier = p.tier();
        MutableComponent stars = Component.literal("★".repeat(tier.stars()) + "☆".repeat(5 - tier.stars()) + " ")
                .withStyle(Style.EMPTY.withColor(tier.color() & 0xFFFFFF));
        stars.append(Component.translatable("tier.fullcourse." + tier.stars()));
        if (tasted) stars.append(Component.literal(" ✔ ").append(Component.translatable("tooltip.fullcourse.tasted")).withStyle(ChatFormatting.GREEN));
        out.add(stars);
        if (!p.givesMeal()) {
            String raw = p.ingredient() ? "tooltip.fullcourse.raw" : "tooltip.fullcourse.raw_only";
            out.add(Component.translatable(raw + (regenerates ? "" : ".no_heal")).withStyle(ChatFormatting.GRAY));
            if (nutrition > 0) out.add(heal(nutrition).withStyle(ChatFormatting.GRAY));
        } else {
            for (Family family : p.families()) {
                if (family == Family.MENDING && (hunger || !regenerates || FullCourseConfig.recoveryIntervalTicks() <= 0)) {
                    // Mending speeds Recovery up, which vanilla hunger, the gamerule or an interval of 0 turns off
                    out.add(benefit(family, tier.level()).withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable(hunger ? "tooltip.fullcourse.no_recovery" : "tooltip.fullcourse.recovery_off")
                                    .withStyle(ChatFormatting.DARK_GRAY)));
                } else {
                    out.add(benefit(family, tier.level()).withStyle(ChatFormatting.BLUE));
                }
            }
            // why a feast's benefits are a level above a gourmet dish's
            if (tier == Tier.FEAST) out.add(Component.translatable("tooltip.fullcourse.feast_bonus").withStyle(ChatFormatting.LIGHT_PURPLE));
            MutableComponent lasts = Component.translatable("tooltip.fullcourse.lasts",
                    duration(tier.durationTicks(FullCourseConfig.durationMultiplier())));
            if (nutrition > 0) lasts.append(Component.literal(" · ")).append(heal(nutrition));
            out.add(lasts.withStyle(ChatFormatting.GRAY));
        }
        if (shift) {
            out.add(chain(p).withStyle(ChatFormatting.GRAY));
            out.add(score(p).withStyle(ChatFormatting.GRAY));
        } else if (p.steps() > 0) {
            out.add(Component.translatable("tooltip.fullcourse.shift").withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    /** "2 steps · 4 stations, crock ×2 · 4 ingredients": the recipe chain's counts, the crock's weight named. */
    static MutableComponent chain(Profile p) {
        Component stations = p.stationPoints() > p.stations()
                ? Component.translatable("tooltip.fullcourse.chain.stations.crock" + (p.stations() == 1 ? ".one" : ""), p.stations())
                : count("stations", p.stations());
        return Component.translatable("tooltip.fullcourse.chain", count("steps", p.steps()), stations, count("ingredients", p.ingredients()));
    }

    /**
     * "8 = 2 + 5 + 1": the score as its parts (steps, stations, a point per three ingredients, and a placed dish's
     * bonus), or just the score when they don't make it.
     */
    private static String sum(Profile p) {
        int assembled = p.score() - p.steps() - p.stationPoints() - p.ingredients() / 3;
        if (p.steps() == 0 || assembled < 0 || p.stationPoints() < p.stations()) return Integer.toString(p.score());
        return p.score() + " = " + p.steps() + " + " + p.stationPoints() + " + " + p.ingredients() / 3 + (assembled > 0 ? " + " + assembled : "");
    }

    /** "1 step", "3 steps": one of the recipe chain's counts. */
    private static Component count(String part, int n) {
        return Component.translatable("tooltip.fullcourse.chain." + part + (n == 1 ? ".one" : ""), n);
    }

    /** "Vigor II: +4 max health": the family, its level and what it does at that level. */
    public static MutableComponent benefit(Family family, int level) {
        return Component.translatable("family.fullcourse." + family.key())
                .append(Component.literal(" "))
                .append(Component.translatable("enchantment.level." + level))
                .append(Component.literal(": "))
                .append(Component.translatable("family.fullcourse." + family.key() + ".effect", effectArgs(family, level)));
    }

    /**
     * The action-bar line on eating a meal: "Rabbit Stew: Vigor II, Mending II · 10:00", and "· replaces Steak" when
     * it took a full row's meal with the least time left ({@code replaced}, else null). A family another held meal
     * gives at a higher level ({@code others}) is grey: the strongest wins, so this meal adds nothing there.
     */
    public static Component mealMessage(Component dish, Profile p, Map<Family, Integer> others, Component replaced) {
        MutableComponent families = Component.empty();
        for (int i = 0; i < p.families().size(); i++) {
            Family family = p.families().get(i);
            if (i > 0) families.append(Component.literal(", "));
            MutableComponent named = Component.translatable("family.fullcourse." + family.key())
                    .append(Component.literal(" "))
                    .append(Component.translatable("enchantment.level." + p.tier().level()));
            families.append(others.getOrDefault(family, 0) > p.tier().level() ? named.withStyle(ChatFormatting.GRAY) : named);
        }
        MutableComponent line = Component.translatable("message.fullcourse.meal", dish, families,
                duration(p.tier().durationTicks(FullCourseConfig.durationMultiplier())));
        if (replaced != null) line = Component.translatable("message.fullcourse.meal_replaces", line, replaced);
        return line.withStyle(Style.EMPTY.withColor(p.tier().color() & 0xFFFFFF));
    }

    /** The action-bar line when meals run out: "Rabbit Stew wore off", "Steak, Bread wore off". */
    public static Component mealEnded(List<Component> dishes) {
        return Component.translatable("message.fullcourse.meal_ended", ComponentUtils.formatList(dishes, Component.literal(", ")))
                .withStyle(ChatFormatting.GRAY);
    }

    /** The numbers in a family's effect text, from the same table the attribute modifiers use. */
    static Object[] effectArgs(Family family, int level) {
        if (family == Family.MENDING) return new Object[] {number(50.0 * level)}; // Recovery.interval: 1 + 0.5 per level
        List<Benefits.Effect> effects = Benefits.EFFECTS.get(family);
        Object[] args = new Object[effects.size()];
        for (int i = 0; i < effects.size(); i++) {
            Benefits.Effect e = effects.get(i);
            double v = Math.abs(e.perLevel() * level);
            // water movement efficiency is a share of water's drag taken away (1 = none), so it reads as a percentage;
            // max health reads in hearts, as the heals do
            boolean percent = e.operation() != Operation.ADD_VALUE || e.attribute().get() == Attributes.WATER_MOVEMENT_EFFICIENCY;
            boolean hearts = e.attribute().get() == Attributes.MAX_HEALTH;
            args[i] = number(percent ? v * 100 : hearts ? v / 2 : v);
        }
        return args;
    }

    /** "Heals 4 ❤ over 8 s": nutrition HP at 1 HP a second. */
    static MutableComponent heal(int nutrition) {
        return Component.translatable("tooltip.fullcourse.heal", number(nutrition / 2.0), nutrition);
    }

    /**
     * "Score 5 · Hearty: 5–7", the scores of the dish's own tier (a scale, not a goal: a dish can't be cooked better),
     * "Feast: 12+" for the top tier, or "set to Simple" when data or a rule set the tier, not the score.
     */
    static MutableComponent score(Profile p) {
        int[] thresholds = FullCourseConfig.tierThresholds();
        Component name = Component.translatable("tier.fullcourse." + p.tier().ordinal());
        if (Tier.fromScore(p.score(), thresholds) != p.tier()) {
            return Component.translatable("tooltip.fullcourse.score_set", p.score(), name);
        }
        int tier = p.tier().ordinal(); // thresholds[i] is the lowest score of tier i + 1
        int lowest = tier == 0 ? 0 : thresholds[tier - 1];
        if (tier >= thresholds.length || tier >= Tier.FEAST.ordinal()) {
            return Component.translatable("tooltip.fullcourse.score_top", sum(p), name, lowest);
        }
        int highest = thresholds[tier] - 1;
        if (highest <= lowest) return Component.translatable("tooltip.fullcourse.score_one", sum(p), name, lowest);
        return Component.translatable("tooltip.fullcourse.score", sum(p), name, lowest, highest);
    }

    /** "30", "2.5": one decimal at most, none for a whole number (rounded first: 0.1 × 3 × 100 is 30.000000000000004). */
    public static String number(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? Long.toString(Math.round(r)) : String.format(java.util.Locale.ROOT, "%.1f", r);
    }

    /** m:ss */
    public static String duration(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
