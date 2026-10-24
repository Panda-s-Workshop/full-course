package com.panda.fullcourse.platform;

import com.panda.fullcourse.food.FoodState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;

/** What common code needs from the mod loader. Each loader module provides one, found with {@link Services}. */
public interface FullCoursePlatform {
    /** "neoforge" or "fabric". */
    String loaderName();

    /** Whether a mod with this id is installed. */
    boolean isModLoaded(String modId);

    /** The display name of the mod that owns a namespace, or the namespace itself. */
    String modName(String namespace);

    /** A builder for a creative tab of the mod's own, which the loader places among its pages. */
    CreativeModeTab.Builder tabBuilder();

    /** The player's meals, heal-over-time, tasted dishes and unlocked slots. Saved, kept on death, synced to the owner. */
    FoodState foodState(Player player);

    /** Stores the player's food state and syncs it to that player's client. */
    void setFoodState(Player player, FoodState state);

    /** Sends a payload to one player, if their client knows it (a vanilla client or a GameTest mock player doesn't). */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
