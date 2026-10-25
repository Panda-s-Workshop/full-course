package com.panda.fullcourse.network;

import com.panda.fullcourse.FullCourse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: whether the {@code naturalHealthRegeneration} gamerule is on, which the client can't read. Food heals
 * and Recovery runs only while it is, so tooltips and the meal list say so. Sent at login and whenever it changes.
 */
public record RulesPayload(boolean regenerates) implements CustomPacketPayload {
    public static final Type<RulesPayload> TYPE = new Type<>(FullCourse.id("rules"));
    public static final StreamCodec<ByteBuf, RulesPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(RulesPayload::new, RulesPayload::regenerates);

    @Override
    public Type<RulesPayload> type() {
        return TYPE;
    }
}
