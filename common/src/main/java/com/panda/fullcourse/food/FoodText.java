package com.panda.fullcourse.food;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.logic.Tier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        List<Component> out = new ArrayList<>();
        // with vanilla hunger food fills the bar instead of healing over time, and Recovery (Mending's work) is off
        boolean hunger = FullCourseConfig.keepVanillaHunger();
        if (hunger) nutrition = 0;
        if (p.remedy()) { // no tier, so no stars
            out.add(Component.translatable("tooltip.fullcourse.remedy").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (nutrition > 0) out.add(heal(nutrition).withStyle(ChatFormatting.GRAY));
            return out;
        }
        Tier tier = p.tier();
        MutableComponent stars = Component.literal("★".repeat(tier.stars()) + "☆".repeat(5 - tier.stars()) + " ")
                .withStyle(Style.EMPTY.withColor(tier.color() & 0xFFFFFF));
        stars.append(Component.translatable("tier.fullcourse." + tier.stars()));
        if (tasted) stars.append(Component.literal(" ✔").withStyle(ChatFormatting.GREEN));
        out.add(stars);
        if (!p.givesMeal()) {
            out.add(Component.translatable("tooltip.fullcourse.raw").withStyle(ChatFormatting.GRAY));
            if (nutrition > 0) out.add(heal(nutrition).withStyle(ChatFormatting.GRAY));
        } else {
            for (Family family : p.families()) {
                if (hunger && family == Family.MENDING) {
                    out.add(benefit(family, tier.level()).withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable("tooltip.fullcourse.no_recovery").withStyle(ChatFormatting.DARK_GRAY)));
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
            out.add(Component.translatable("tooltip.fullcourse.chain", count("steps", p.steps()), count("stations", p.stations()),
                    count("ingredients", p.ingredients())).withStyle(ChatFormatting.GRAY));
            out.add(score(p).withStyle(ChatFormatting.GRAY));
        } else if (p.steps() > 0) {
            out.add(Component.translatable("tooltip.fullcourse.shift").withStyle(ChatFormatting.GRAY));
        }
        return out;
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

    /** The action-bar line on eating a meal: "Rabbit Stew: Vigor II, Mending II · 10:00". */
    public static Component mealMessage(Component dish, Profile p) {
        MutableComponent families = Component.empty();
        for (int i = 0; i < p.families().size(); i++) {
            if (i > 0) families.append(Component.literal(", "));
            families.append(Component.translatable("family.fullcourse." + p.families().get(i).key()))
                    .append(Component.literal(" "))
                    .append(Component.translatable("enchantment.level." + p.tier().level()));
        }
        return Component.translatable("message.fullcourse.meal", dish, families,
                duration(p.tier().durationTicks(FullCourseConfig.durationMultiplier()))).withStyle(Style.EMPTY.withColor(p.tier().color() & 0xFFFFFF));
    }

    /** The numbers in a family's effect text, from the same table the attribute modifiers use. */
    static Object[] effectArgs(Family family, int level) {
        if (family == Family.MENDING) return new Object[] {number(50.0 * level)}; // Recovery.interval: 1 + 0.5 per level
        List<Benefits.Effect> effects = Benefits.EFFECTS.get(family);
        Object[] args = new Object[effects.size()];
        for (int i = 0; i < effects.size(); i++) {
            Benefits.Effect e = effects.get(i);
            double v = Math.abs(e.perLevel() * level);
            // water movement efficiency is a share of water's drag taken away (1 = none), so it reads as a percentage
            boolean percent = e.operation() != Operation.ADD_VALUE || e.attribute().get() == Attributes.WATER_MOVEMENT_EFFICIENCY;
            args[i] = number(percent ? v * 100 : v);
        }
        return args;
    }

    /** "Heals 4 ❤ over 8 s": nutrition HP at 1 HP a second. */
    static MutableComponent heal(int nutrition) {
        return Component.translatable("tooltip.fullcourse.heal", number(nutrition / 2.0), nutrition);
    }

    /** "Score 5 · Gourmet at 8", the top tier, or "set to Simple" when data or a rule set the tier, not the score. */
    static MutableComponent score(Profile p) {
        int[] thresholds = FullCourseConfig.tierThresholds();
        if (Tier.fromScore(p.score(), thresholds) != p.tier()) {
            return Component.translatable("tooltip.fullcourse.score_set", p.score(), Component.translatable("tier.fullcourse." + p.tier().ordinal()));
        }
        int next = p.tier().ordinal(); // thresholds[i] is the lowest score of tier i + 1
        if (next >= thresholds.length || next >= Tier.FEAST.ordinal()) return Component.translatable("tooltip.fullcourse.score_top", p.score());
        return Component.translatable("tooltip.fullcourse.score", p.score(),
                Component.translatable("tier.fullcourse." + (next + 1)), thresholds[next]);
    }

    /** "30", "2.5": one decimal at most, none for a whole number (rounded first: 0.1 × 3 × 100 is 30.000000000000004). */
    static String number(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? Long.toString(Math.round(r)) : String.format(java.util.Locale.ROOT, "%.1f", r);
    }

    /** m:ss */
    public static String duration(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
