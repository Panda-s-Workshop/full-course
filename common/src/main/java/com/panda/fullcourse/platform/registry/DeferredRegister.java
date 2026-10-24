package com.panda.fullcourse.platform.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The mod's registrations for one registry, written without a loader: entries are collected here and each loader
 * registers them (NeoForge from its register event, Fabric at startup) through {@link #registerAll}. Shaped like
 * NeoForge's DeferredRegister so the registry classes read the same.
 */
public class DeferredRegister<R> {
    private static final List<DeferredRegister<?>> ALL = new ArrayList<>();

    private final ResourceKey<? extends Registry<R>> registryKey;
    private final String namespace;
    private final List<DeferredHolder<R, ? extends R>> entries = new ArrayList<>();

    protected DeferredRegister(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
        this.registryKey = registryKey;
        this.namespace = namespace;
        ALL.add(this);
    }

    public static <R> DeferredRegister<R> create(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
        return new DeferredRegister<>(registryKey, namespace);
    }

    public static Items createItems(String namespace) {
        return new Items(namespace);
    }

    public static Blocks createBlocks(String namespace) {
        return new Blocks(namespace);
    }

    public static Entities createEntities(String namespace) {
        return new Entities(namespace);
    }

    public static DataComponents createDataComponents(ResourceKey<? extends Registry<DataComponentType<?>>> registryKey, String namespace) {
        return new DataComponents(registryKey, namespace);
    }

    /** Every register the mod made, in the order they were created. */
    public static List<DeferredRegister<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    public ResourceKey<? extends Registry<R>> getRegistryKey() {
        return registryKey;
    }

    @SuppressWarnings("unchecked")
    protected ResourceKey<R> key(String name) {
        return ResourceKey.create((ResourceKey<? extends Registry<R>>) registryKey, Identifier.fromNamespaceAndPath(namespace, name));
    }

    protected <I extends R, H extends DeferredHolder<R, I>> H add(H holder) {
        entries.add(holder);
        return holder;
    }

    public <I extends R> DeferredHolder<R, I> register(String name, Supplier<? extends I> factory) {
        return add(new DeferredHolder<>(key(name), factory));
    }

    public <I extends R> DeferredHolder<R, I> register(String name, Function<Identifier, ? extends I> factory) {
        ResourceKey<R> key = key(name);
        return add(new DeferredHolder<>(key, () -> factory.apply(key.identifier())));
    }

    public List<DeferredHolder<R, ? extends R>> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** For the loader: creates every entry in order and hands it over to be put in the registry. */
    public void registerAll(BiConsumer<ResourceKey<R>, R> sink) {
        for (DeferredHolder<R, ? extends R> holder : entries) sink.accept(holder.getKey(), holder.create());
    }

    public static final class Items extends DeferredRegister<Item> {
        Items(String namespace) {
            super(Registries.ITEM, namespace);
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> properties) {
            ResourceKey<Item> key = key(name);
            return add(new DeferredItem<>(key, () -> factory.apply(properties.apply(new Item.Properties().setId(key)))));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, UnaryOperator.identity());
        }

        public DeferredItem<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
            return registerItem(name, Item::new, properties);
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return registerSimpleItem(name, UnaryOperator.identity());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<? extends Block> block) {
            return registerItem(block.getId().getPath(), p -> new BlockItem(block.get(), p), Item.Properties::useBlockDescriptionPrefix);
        }
    }

    public static final class Blocks extends DeferredRegister<Block> {
        Blocks(String namespace) {
            super(Registries.BLOCK, namespace);
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
                UnaryOperator<BlockBehaviour.Properties> properties) {
            ResourceKey<Block> key = key(name);
            return add(new DeferredBlock<>(key, () -> factory.apply(properties.apply(BlockBehaviour.Properties.of().setId(key)))));
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
            return registerBlock(name, Block::new, properties);
        }
    }

    public static final class Entities extends DeferredRegister<EntityType<?>> {
        Entities(String namespace) {
            super(Registries.ENTITY_TYPE, namespace);
        }

        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name, EntityType.EntityFactory<E> factory,
                MobCategory category, UnaryOperator<EntityType.Builder<E>> builder) {
            ResourceKey<EntityType<?>> key = key(name);
            return add(new DeferredHolder<>(key, () -> builder.apply(EntityType.Builder.of(factory, category)).build(key)));
        }
    }

    public static final class DataComponents extends DeferredRegister<DataComponentType<?>> {
        DataComponents(ResourceKey<? extends Registry<DataComponentType<?>>> registryKey, String namespace) {
            super(registryKey, namespace);
        }

        public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            return register(name, () -> builder.apply(DataComponentType.builder()).build());
        }
    }
}
