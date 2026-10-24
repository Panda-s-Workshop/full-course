package com.panda.fullcourse.registry;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.platform.registry.DeferredHolder;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * {@code fullcourse:recovery_rate}: how fast Recovery heals. The interval between heals is the config's interval
 * divided by it, so 1 is the default pace, Mending adds 0.5 a level and 0 stops Recovery. Players have it, so any mod
 * or command that changes attributes can change Recovery.
 */
public final class ModAttributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, FullCourse.MODID);

    public static final DeferredHolder<Attribute, Attribute> RECOVERY_RATE = ATTRIBUTES.register("recovery_rate",
            () -> new RangedAttribute("attribute.name.fullcourse.recovery_rate", 1.0, 0.0, 16.0).setSyncable(true));

    private ModAttributes() {}
}
