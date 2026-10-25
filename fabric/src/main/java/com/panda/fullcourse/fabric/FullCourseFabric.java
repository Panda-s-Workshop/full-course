package com.panda.fullcourse.fabric;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.command.FullCourseCommand;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.network.ProfilesPayload;
import com.panda.fullcourse.network.RecoveryPayload;
import com.panda.fullcourse.network.RulesPayload;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import com.panda.fullcourse.registry.ModAttributes;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.levelgen.GenerationStep;

/** The Fabric entry point: registers the common content and wires the common hooks to Fabric API's events. */
public final class FullCourseFabric implements ModInitializer {
    /**
     * Registries whose entries others are made from, registered first in this order: items take blocks, components and
     * block entity types. The rest follow in the order the mod created them.
     */
    private static final List<ResourceKey<?>> FIRST = List.of(Registries.BLOCK, Registries.DATA_COMPONENT_TYPE, Registries.SOUND_EVENT,
            Registries.PARTICLE_TYPE, Registries.ENTITY_TYPE, Registries.BLOCK_ENTITY_TYPE, Registries.ITEM);

    @Override
    public void onInitialize() {
        FullCourseConfig.use(FabricConfig.load());
        FullCourse.init();
        DeferredRegister.all().stream()
                .sorted(Comparator.comparingInt(r -> {
                    int i = FIRST.indexOf(r.getRegistryKey());
                    return i < 0 ? FIRST.size() : i;
                }))
                .forEach(FullCourseFabric::registerAll);
        // players get Recovery's rate, added to their existing attributes (re-registering the set would replace other mods')
        FabricDefaultAttributeRegistry.MODIFY.register(ctx -> ctx.modify(EntityTypes.PLAYER, (type, builder) -> builder.add(ModAttributes.RECOVERY_RATE.holder())));

        PayloadTypeRegistry.clientboundPlay().register(ProfilesPayload.TYPE, ProfilesPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RecoveryPayload.TYPE, RecoveryPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RulesPayload.TYPE, RulesPayload.STREAM_CODEC);
        ModRecipes.kitchenSerializers().forEach(RecipeSynchronization::synchronizeRecipeSerializer); // for recipe viewers
        // wild onions and ember peppers (NeoForge reads data/fullcourse/neoforge/biome_modifier/*.json instead)
        BiomeModifications.addFeature(BiomeSelectors.tag(TagKey.create(Registries.BIOME, FullCourse.id("has_wild_onions"))),
                GenerationStep.Decoration.VEGETAL_DECORATION, ResourceKey.create(Registries.PLACED_FEATURE, FullCourse.id("patch_wild_onions")));
        BiomeModifications.addFeature(BiomeSelectors.tag(TagKey.create(Registries.BIOME, FullCourse.id("has_wild_ember_peppers"))),
                GenerationStep.Decoration.VEGETAL_DECORATION, ResourceKey.create(Registries.PLACED_FEATURE, FullCourse.id("patch_wild_ember_peppers")));
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(FoodRules::tick));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) FoodRules.onDeath(player);
        });
        // at login and after /reload: the food table follows the recipes
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> Services.PLATFORM.sendToPlayer(player, ProfilesPayload.of(player)));
        ServerLifecycleEvents.SERVER_STARTED.register(FoodProfiles::all);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> FullCourseCommand.register(dispatcher, context));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FoodProfiles.reset());
    }

    @SuppressWarnings("unchecked")
    private static <R> void registerAll(DeferredRegister<R> register) {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getValue(register.getRegistryKey().identifier());
        if (registry == null) throw new IllegalStateException("No registry " + register.getRegistryKey().identifier());
        register.registerAll((key, value) -> Registry.register(registry, key, value));
    }
}
