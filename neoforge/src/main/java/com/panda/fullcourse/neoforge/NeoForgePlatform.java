package com.panda.fullcourse.neoforge;

import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.platform.FullCoursePlatform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/** {@link FullCoursePlatform} on NeoForge: data attachments, the payload channel and the mod list. */
public final class NeoForgePlatform implements FullCoursePlatform {
    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public boolean wrapsTooltips() {
        return true;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public FoodState foodState(Player player) {
        return player.getData(NeoForgeAttachments.FOOD);
    }

    @Override
    public void setFoodState(Player player, FoodState state) {
        player.setData(NeoForgeAttachments.FOOD, state);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }
}
