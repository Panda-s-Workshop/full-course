package com.panda.fullcourse.platform.registry;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/** One entry of a {@link DeferredRegister}: empty until the loader registers it, then the registered value. */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    private final ResourceKey<R> key;
    private final Supplier<? extends T> factory;
    private @Nullable T value;

    DeferredHolder(ResourceKey<R> key, Supplier<? extends T> factory) {
        this.key = key;
        this.factory = factory;
    }

    /** Creates the value; called once, by the loader, while the registry is open. */
    T create() {
        if (value == null) value = factory.get();
        return value;
    }

    @Override
    public T get() {
        if (value == null) throw new IllegalStateException("Registry entry " + key.identifier() + " is not registered yet");
        return value;
    }

    public T value() {
        return get();
    }

    public boolean isBound() {
        return value != null;
    }

    public Identifier getId() {
        return key.identifier();
    }

    public ResourceKey<R> getKey() {
        return key;
    }

    /** The registry's holder for this entry, for APIs that take a Holder (music, enchantment effects). */
    @SuppressWarnings("unchecked")
    public Holder<R> holder() {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getValue(key.registry());
        if (registry == null) throw new IllegalStateException("No built-in registry " + key.registry());
        return registry.get(key).map(h -> (Holder<R>) h).orElseThrow(() -> new IllegalStateException(key.identifier() + " is not registered"));
    }
}
