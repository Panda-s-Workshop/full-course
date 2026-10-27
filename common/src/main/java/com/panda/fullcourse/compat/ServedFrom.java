package com.panda.fullcourse.compat;

import com.panda.fullcourse.FullCourse;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dishes served from a block, as Farmer's Delight's feasts and pies are and its add-ons' built on them: each serving
 * item, mapped to the block's item. Read from the block's own {@code getServingItem(BlockState)} (over each of its
 * states, as a medley serves several) and {@code getPieSliceItem()}, reflectively, so nothing here needs the other mod
 * and an add-on's feast needs no data.
 *
 * <p>Each method is looked up by name and type through a method handle, never by listing the class's methods: listing
 * them resolves every signature, and on a dedicated server a block with a method taking a client-only type (Steam 'n'
 * Rails' boiler takes a {@code PoseStack}) then throws {@link NoClassDefFoundError}.
 */
public final class ServedFrom {
    private static final MethodType PER_STATE = MethodType.methodType(ItemStack.class, BlockState.class);
    private static final MethodType SLICE = MethodType.methodType(ItemStack.class);
    /** Each block class's two handles, either of them absent; looked up once per class. */
    private static final Map<Class<?>, Handles> HANDLES = new ConcurrentHashMap<>();

    private record Handles(Optional<MethodHandle> perState, Optional<MethodHandle> slice) {}

    private ServedFrom() {}

    public static Map<Item, Item> servings() {
        Map<Item, Item> out = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem blockItem)) continue;
            Block block = blockItem.getBlock();
            if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(FullCourse.MODID)) continue; // ours give meals as blocks
            for (Item serving : servingsOf(block)) out.putIfAbsent(serving, item);
        }
        return out;
    }

    static Set<Item> servingsOf(Block block) {
        Set<Item> out = new LinkedHashSet<>();
        Handles handles = HANDLES.computeIfAbsent(block.getClass(),
                type -> new Handles(find(type, "getServingItem", PER_STATE), find(type, "getPieSliceItem", SLICE)));
        handles.perState().ifPresent(h -> {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) add(out, block, h, state);
        });
        handles.slice().ifPresent(h -> add(out, block, h));
        out.remove(block.asItem());
        return out;
    }

    /**
     * The public method {@code name} of {@code type} with exactly that {@code methodType}, found on the class or the
     * nearest public class above it that has it (a mod's block class may itself be package-private); empty if none.
     */
    static Optional<MethodHandle> find(Class<?> type, String name, MethodType methodType) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return Optional.of(lookup.findVirtual(c, name, methodType));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // not declared here, or here but not reachable: try the class above
            } catch (LinkageError e) {
                LinkageWarnings.once(c, name, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static void add(Set<Item> out, Block block, MethodHandle handle, Object... args) {
        try {
            Object[] call = new Object[args.length + 1];
            call[0] = block;
            System.arraycopy(args, 0, call, 1, args.length);
            if (handle.invokeWithArguments(call) instanceof ItemStack stack && !stack.isEmpty()) out.add(stack.getItem());
        } catch (LinkageError e) {
            LinkageWarnings.once(block.getClass(), "a serving", e);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            // a state the block won't serve from
        }
    }
}
