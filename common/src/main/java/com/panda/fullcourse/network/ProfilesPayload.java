package com.panda.fullcourse.network;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.Profile;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Server → client: every food's profile and the server's config, sent at login and after each {@code /reload} (the
 * client has no recipes, and its own config file may differ from the server's).
 */
public record ProfilesPayload(Map<Identifier, Profile> profiles, FullCourseConfig.Snapshot config) implements CustomPacketPayload {
    public static final Type<ProfilesPayload> TYPE = new Type<>(FullCourse.id("profiles"));
    public static final int MAX_FOODS = 65536;
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfilesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, Profile.STREAM_CODEC, MAX_FOODS), p -> new HashMap<>(p.profiles()),
            FullCourseConfig.Snapshot.STREAM_CODEC, ProfilesPayload::config,
            ProfilesPayload::new);

    @Override
    public Type<ProfilesPayload> type() {
        return TYPE;
    }

    /** The server's current table, for one player. */
    public static ProfilesPayload of(ServerPlayer player) {
        Map<Identifier, Profile> out = new HashMap<>();
        FoodProfiles.all(player.level().getServer()).forEach((item, profile) -> out.put(BuiltInRegistries.ITEM.getKey(item), profile));
        return new ProfilesPayload(out, FullCourseConfig.snapshot());
    }

    /** On the client: use the server's config and store the table. */
    public void apply() {
        FullCourseConfig.useServer(config);
        Map<Item, Profile> byItem = new HashMap<>();
        profiles.forEach((id, profile) -> BuiltInRegistries.ITEM.get(id).ifPresent(h -> byItem.put(h.value(), profile)));
        FoodProfiles.setClient(byItem);
    }
}
