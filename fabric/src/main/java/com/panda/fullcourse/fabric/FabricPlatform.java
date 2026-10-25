package com.panda.fullcourse.fabric;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.platform.FullCoursePlatform;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;

/** {@link FullCoursePlatform} on Fabric: Fabric API data attachments and networking, and Fabric Loader's mod list. */
public final class FabricPlatform implements FullCoursePlatform {
    /** Meals, heal-over-time, tasted dishes, unlocked slots. Saved, kept on death, synced to the owning player only. */
    static final AttachmentType<FoodState> FOOD = AttachmentRegistry.create(FullCourse.id("food"), builder -> builder
            .initializer(() -> FoodState.EMPTY)
            .persistent(FoodState.CODEC)
            .copyOnDeath()
            .syncWith(FoodState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public boolean wrapsTooltips() {
        return false;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String modName(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace).map(c -> c.getMetadata().getName()).orElse(namespace);
    }

    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return FabricCreativeModeTab.builder();
    }

    @Override
    public FoodState foodState(Player player) {
        return player.getAttachedOrElse(FOOD, FoodState.EMPTY);
    }

    @Override
    public void setFoodState(Player player, FoodState state) {
        player.setAttached(FOOD, state);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }
}
