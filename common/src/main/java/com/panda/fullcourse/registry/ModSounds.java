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
    /** The Tasting Journal unlocks a meal slot: a chime with a quiet fanfare under it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SLOT_UNLOCK = sound("slot.unlock");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLOT_UNLOCK_FANFARE = sound("slot.unlock_fanfare");
    /** A knife stroke on the cutting board. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOARD_CHOP = sound("board.chop");
    /** A cooking pot simmering, now and then. */
    public static final DeferredHolder<SoundEvent, SoundEvent> POT_BUBBLE = sound("pot.bubble");
    /** The pot finishes a dish: a surge of bubbles with a hiss of steam. */
    public static final DeferredHolder<SoundEvent, SoundEvent> POT_DONE = sound("pot.done");
    public static final DeferredHolder<SoundEvent, SoundEvent> POT_DONE_HISS = sound("pot.done_hiss");
    /** A lit brick oven, now and then. */
    public static final DeferredHolder<SoundEvent, SoundEvent> OVEN_CRACKLE = sound("oven.crackle");
    /** The oven finishes a dish: a low crackle with a brighter one over it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> OVEN_DONE = sound("oven.done");
    public static final DeferredHolder<SoundEvent, SoundEvent> OVEN_DONE_CRACKLE = sound("oven.done_crackle");
    /** Food goes into a fermenting crock, or the crock won't take it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CROCK_FILL = sound("crock.fill");
    public static final DeferredHolder<SoundEvent, SoundEvent> CROCK_REFUSE = sound("crock.refuse");
    /** The crock's lid goes on, or comes off a finished batch. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CROCK_SEAL = sound("crock.seal");
    public static final DeferredHolder<SoundEvent, SoundEvent> CROCK_OPEN = sound("crock.open");
    /** A finished crock bubbles now and then. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CROCK_BUBBLE = sound("crock.bubble");
    /** A serving taken from a feast. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FEAST_SERVE = sound("feast.serve");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, id -> SoundEvent.createVariableRangeEvent(id));
    }
}
