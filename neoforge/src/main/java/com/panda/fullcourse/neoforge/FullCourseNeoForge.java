package com.panda.fullcourse.neoforge;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.command.FullCourseCommand;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.network.ProfilesPayload;
import com.panda.fullcourse.network.RecoveryPayload;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import com.panda.fullcourse.registry.ModAttributes;
import com.panda.fullcourse.registry.ModRecipes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** The NeoForge entry point: registers the common content and wires the common hooks to NeoForge's events. */
@Mod(FullCourse.MODID)
public final class FullCourseNeoForge {
    public FullCourseNeoForge(IEventBus modBus, ModContainer container) {
        FullCourse.init();
        modBus.addListener((RegisterEvent e) -> DeferredRegister.all().forEach(register -> registerInto(e, register)));
        NeoForgeAttachments.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent e) -> e.add(EntityTypes.PLAYER, ModAttributes.RECOVERY_RATE.holder()));
        container.registerConfig(ModConfig.Type.SYNCED, NeoForgeConfig.SPEC);
        FullCourseConfig.use(new NeoForgeConfig());
        // the client handler is registered by the client entry class
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToClient(ProfilesPayload.TYPE, ProfilesPayload.STREAM_CODEC)
                .playToClient(RecoveryPayload.TYPE, RecoveryPayload.STREAM_CODEC));

        var game = NeoForge.EVENT_BUS;
        game.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) FoodRules.tick(player);
        });
        // last, so a mod that cancels the death (a totem-like item) keeps the meals
        game.addListener(EventPriority.LOWEST, false, (LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) FoodRules.onDeath(player);
        });
        // fires for one player at login and for everyone after /reload: the food table follows the recipes
        game.addListener((OnDatapackSyncEvent e) -> {
            e.getRelevantPlayers().forEach(p -> Services.PLATFORM.sendToPlayer(p, ProfilesPayload.of(p)));
            e.sendRecipes(ModRecipes.kitchenTypes()); // for recipe viewers on the client
        });
        game.addListener((ServerStartedEvent e) -> FoodProfiles.all(e.getServer()));
        game.addListener((RegisterCommandsEvent e) -> FullCourseCommand.register(e.getDispatcher(), e.getBuildContext()));
        game.addListener((ServerStoppedEvent e) -> FoodProfiles.reset());

        com.panda.fullcourse.gametest.FullCourseGameTests.register(modBus);
    }

    private static <R> void registerInto(RegisterEvent event, DeferredRegister<R> register) {
        event.register(register.getRegistryKey(), helper -> register.registerAll(helper::register));
    }
}
