package com.panda.fullcourse.network;

import com.panda.fullcourse.FullCourse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: Recovery just healed you. The client shows a heart if its {@code showRecoveryParticles} is on. */
public record RecoveryPayload() implements CustomPacketPayload {
    public static final RecoveryPayload INSTANCE = new RecoveryPayload();
    public static final Type<RecoveryPayload> TYPE = new Type<>(FullCourse.id("recovery"));
    public static final StreamCodec<ByteBuf, RecoveryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<RecoveryPayload> type() {
        return TYPE;
    }
}
