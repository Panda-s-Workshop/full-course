package com.panda.fullcourse.neoforge;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.food.FoodState;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data attachments on NeoForge: each player's food state. */
public final class NeoForgeAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FullCourse.MODID);

    /** Meals, heal-over-time, tasted dishes, unlocked slots. Saved, kept on death, synced to the owning player only. */
    public static final Supplier<AttachmentType<FoodState>> FOOD = ATTACHMENTS.register("food",
            () -> AttachmentType.builder(() -> FoodState.EMPTY)
                    .serialize(FoodState.CODEC.fieldOf("food"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, FoodState.STREAM_CODEC)
                    .build());

    private NeoForgeAttachments() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
