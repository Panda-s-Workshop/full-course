package com.panda.fullcourse.neoforge.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.client.BrickOvenScreen;
import com.panda.fullcourse.client.ClientSync;
import com.panda.fullcourse.client.CookingPotScreen;
import com.panda.fullcourse.client.CuttingBoardRenderer;
import com.panda.fullcourse.client.DevExit;
import com.panda.fullcourse.client.FoodTooltips;
import com.panda.fullcourse.client.JournalScreen;
import com.panda.fullcourse.client.KitchenRecipes;
import com.panda.fullcourse.client.MealHud;
import com.panda.fullcourse.client.compat.cfb.CookingForBlockheadsClient;
import com.panda.fullcourse.food.TastingJournalItem;
import com.panda.fullcourse.neoforge.client.compat.AppleSkinCompat;
import com.panda.fullcourse.network.ProfilesPayload;
import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModMenus;
import com.panda.fullcourse.registry.ModRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** The NeoForge client entry point: hands the common client registrations NeoForge's events. */
@Mod(value = FullCourse.MODID, dist = Dist.CLIENT)
public final class FullCourseClient {
    public FullCourseClient(IEventBus modBus, ModContainer container) {
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(ProfilesPayload.TYPE, (payload, context) -> ClientSync.received(payload)));
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(ModMenus.COOKING_POT.get(), CookingPotScreen::new);
            e.register(ModMenus.BRICK_OVEN.get(), BrickOvenScreen::new);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(ModBlockEntities.CUTTING_BOARD.get(), CuttingBoardRenderer::new));
        // the meal row sits where the food bar was, above the mount's hearts when riding; it stacks the rows above it
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.VEHICLE_HEALTH, MealHud.LAYER, (g, delta) -> {
            Hud hud = Minecraft.getInstance().gui.hud;
            if (MealHud.extract(g, g.guiHeight() - hud.rightHeight + 9)) hud.rightHeight += MealHud.HEIGHT;
        }));
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre e) -> {
            if (e.getName().equals(VanillaGuiLayers.FOOD_LEVEL) && MealHud.active()) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> FoodTooltips.onTooltip(e.getItemStack(), e.getToolTip()));
        TastingJournalItem.setOpener(JournalScreen::open);
        if (ModList.get().isLoaded("appleskin")) AppleSkinCompat.register();
        if (ModList.get().isLoaded("cookingforblockheads")) CookingForBlockheadsClient.register();
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent e) -> DevExit.onClientStopping());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientSync.disconnected());
        // before recipe viewers read them (JEI listens at the lowest priority); only an event that carries the kitchen's types
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (RecipesReceivedEvent e) -> {
            if (ModRecipes.kitchenTypes().stream().anyMatch(e.getRecipeTypes()::contains)) {
                KitchenRecipes.received(type -> e.getRecipeMap().byType((RecipeType) type));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientSync.tick());
    }
}
