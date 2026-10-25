package com.panda.fullcourse.registry;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.platform.registry.DeferredHolder;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

/**
 * The mod's sound events. {@code assets/fullcourse/sounds.json} points each at vanilla audio and names its subtitle, so
 * a resource pack can give one its own sound.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FullCourse.MODID);

    /** A meal starts, or a meal already held is eaten again. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MEAL_START = sound("meal.start");
    /** A meal runs out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MEAL_END = sound("meal.end");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, id -> SoundEvent.createVariableRangeEvent(id));
    }
}
