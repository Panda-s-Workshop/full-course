package com.panda.fullcourse.food;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.registry.ModAttributes;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Benefit families as attribute modifiers, one transient modifier per family and attribute with the id
 * {@code fullcourse:meal/<family>[_<n>]}. Transient, so they're rebuilt from the held meals after a relog or respawn.
 * Night Eye refreshes vanilla Night Vision; Mending raises {@code fullcourse:recovery_rate}, which Recovery reads ({@link FoodRules}).
 */
public final class Benefits {
    /** One attribute change per level of a family. The attribute is looked up when used: the mod's own is registered late. */
    public record Effect(Supplier<Holder<Attribute>> attribute, double perLevel, Operation operation) {}

    public static final Map<Family, List<Effect>> EFFECTS = effects();
    /** Night Vision lasts this long and is refreshed below {@link #NIGHT_EYE_REFRESH}, so it never flickers. */
    static final int NIGHT_EYE_TICKS = 300;
    static final int NIGHT_EYE_REFRESH = 220;

    private Benefits() {}

    private static Map<Family, List<Effect>> effects() {
        Map<Family, List<Effect>> m = new EnumMap<>(Family.class);
        m.put(Family.BRAWN, List.of(new Effect(() -> Attributes.ATTACK_DAMAGE, 1, Operation.ADD_VALUE)));
        m.put(Family.TIDE, List.of(new Effect(() -> Attributes.OXYGEN_BONUS, 1, Operation.ADD_VALUE),
                new Effect(() -> Attributes.WATER_MOVEMENT_EFFICIENCY, 0.15, Operation.ADD_VALUE)));
        m.put(Family.VIGOR, List.of(new Effect(() -> Attributes.MAX_HEALTH, 2, Operation.ADD_VALUE)));
        m.put(Family.SWIFT, List.of(new Effect(() -> Attributes.MOVEMENT_SPEED, 0.05, Operation.ADD_MULTIPLIED_BASE)));
        m.put(Family.DILIGENCE, List.of(new Effect(() -> Attributes.BLOCK_BREAK_SPEED, 0.10, Operation.ADD_MULTIPLIED_BASE)));
        m.put(Family.FORTUNE, List.of(new Effect(() -> Attributes.LUCK, 1, Operation.ADD_VALUE)));
        m.put(Family.FORTITUDE, List.of(new Effect(() -> Attributes.ARMOR, 1, Operation.ADD_VALUE)));
        m.put(Family.NIGHT_EYE, List.of());
        m.put(Family.EMBER, List.of(new Effect(() -> Attributes.BURNING_TIME, -0.20, Operation.ADD_MULTIPLIED_BASE)));
        m.put(Family.LIGHTFOOT, List.of(new Effect(() -> Attributes.SAFE_FALL_DISTANCE, 2, Operation.ADD_VALUE),
                new Effect(() -> Attributes.JUMP_STRENGTH, 0.05, Operation.ADD_MULTIPLIED_BASE)));
        m.put(Family.MENDING, List.of(new Effect(ModAttributes.RECOVERY_RATE::holder, 0.5, Operation.ADD_VALUE)));
        return m;
    }

    public static Identifier modifierId(Family family, int index) {
        return FullCourse.id("meal/" + family.key() + (index == 0 ? "" : "_" + index));
    }

    /** Sets every family's modifiers to {@code levels} (absent = removed). */
    public static void apply(Player player, Map<Family, Integer> levels) {
        for (Map.Entry<Family, List<Effect>> e : EFFECTS.entrySet()) {
            int level = levels.getOrDefault(e.getKey(), 0);
            List<Effect> effects = e.getValue();
            for (int i = 0; i < effects.size(); i++) {
                Effect effect = effects.get(i);
                AttributeInstance instance = player.getAttribute(effect.attribute().get());
                if (instance == null) continue;
                Identifier id = modifierId(e.getKey(), i);
                if (level <= 0) {
                    instance.removeModifier(id);
                    continue;
                }
                AttributeModifier current = instance.getModifier(id);
                double amount = effect.perLevel() * level;
                if (current == null || current.amount() != amount) {
                    instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, effect.operation()));
                }
            }
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        if (levels.getOrDefault(Family.NIGHT_EYE, 0) > 0) {
            MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
            if (vision == null || vision.getDuration() < NIGHT_EYE_REFRESH) { // also over a potion's last seconds, which flicker
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_EYE_TICKS, 0, true, false, false));
            }
        }
    }
}
