package com.panda.fullcourse.fabric.client;

import com.panda.fullcourse.client.ClientSync;
import com.panda.fullcourse.client.BrickOvenScreen;
import com.panda.fullcourse.client.CookingPotScreen;
import com.panda.fullcourse.client.CuttingBoardRenderer;
import com.panda.fullcourse.client.DevExit;
import com.panda.fullcourse.client.FoodTooltips;
import com.panda.fullcourse.client.JournalScreen;
import com.panda.fullcourse.client.MealHud;
import com.panda.fullcourse.food.TastingJournalItem;
import com.panda.fullcourse.network.ProfilesPayload;
import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModMenus;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The Fabric client entry point: hands the common client registrations to Fabric API's registries and events. */
public final class FullCourseFabricClient implements ClientModInitializer {
    /** Vanilla draws the food row 39 px above the bottom, 9 px tall, and mount hearts in its place when riding. */
    private static final int FOOD_ROW_BOTTOM = 30;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ProfilesPayload.TYPE, (payload, context) -> ClientSync.received(payload));
        MenuScreens.register(ModMenus.COOKING_POT.get(), CookingPotScreen::new);
        MenuScreens.register(ModMenus.BRICK_OVEN.get(), BrickOvenScreen::new);
        BlockEntityRenderers.register(ModBlockEntities.CUTTING_BOARD.get(), CuttingBoardRenderer::new);
        // the meal row replaces the food bar, sits above the mount's hearts when riding, and lifts the air bubbles over it
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, vanilla -> (g, delta) -> {
            if (!MealHud.active()) vanilla.extractRenderState(g, delta);
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.MOUNT_HEALTH, MealHud.LAYER, (g, delta) -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            MealHud.extract(g, g.guiHeight() - FOOD_ROW_BOTTOM - 10 * MealHud.vehicleHeartRows(player));
        });
        HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, vanilla -> (g, delta) -> {
            if (!MealHud.active()) {
                vanilla.extractRenderState(g, delta);
                return;
            }
            g.pose().pushMatrix();
            g.pose().translate(0, -(MealHud.HEIGHT - 10));
            vanilla.extractRenderState(g, delta);
            g.pose().popMatrix();
        });
        // the held item's name sits where the lifted air bubbles now are (and the row, when mount hearts lift it): lift it too
        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, vanilla -> (g, delta) -> {
            var player = Minecraft.getInstance().player;
            if (player == null || !MealHud.active()) {
                vanilla.extractRenderState(g, delta);
                return;
            }
            g.pose().pushMatrix();
            g.pose().translate(0, -(MealHud.HEIGHT - 10 + 10 * MealHud.vehicleHeartRows(player)));
            vanilla.extractRenderState(g, delta);
            g.pose().popMatrix();
        });
        // vanilla's action-bar message (bottom 63 above the screen's bottom) would cover the row once two or more rows of
        // mount hearts lift it (row top 48 + 10 per heart row above the bottom); NeoForge already stacks it above the HUD
        HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, vanilla -> (g, delta) -> {
            var player = Minecraft.getInstance().player;
            int lift = player == null || !MealHud.active() ? 0 : Math.max(0, 10 * MealHud.vehicleHeartRows(player) - 14);
            if (lift == 0) {
                vanilla.extractRenderState(g, delta);
                return;
            }
            g.pose().pushMatrix();
            g.pose().translate(0, -lift);
            vanilla.extractRenderState(g, delta);
            g.pose().popMatrix();
        });
        ItemTooltipCallback.EVENT.register((stack, context, flags, lines) -> FoodTooltips.onTooltip(stack, lines));
        TastingJournalItem.setOpener(JournalScreen::open);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> DevExit.onClientStopping());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientSync.disconnected());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientSync.tick());
    }
}
