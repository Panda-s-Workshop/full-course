package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Fermenting Crock's contents. Open, it takes up to four stacks of things that ferment. Sealed on a matching
 * recipe, nothing goes in or out until the recipe's time has passed: on the world's tick count (so a crock keeps
 * fermenting while its chunk is unloaded) or on the overworld's clock, whichever ran further (so a night slept through
 * counts). Opening a finished crock runs the recipe as many times as the stacks allow, at most
 * {@link #MAX_BATCHES}, and hands back the products, the empties the ingredients leave and any spare items. Breaking
 * a crock early spills the ingredients: the batch is lost, never the food.
 */
public class FermentingCrockBlockEntity extends BlockEntity {
    public static final int SLOTS = FermentingRecipe.MAX_INGREDIENTS;
    public static final int MAX_BATCHES = 16;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private boolean sealed;
    /** World tick count the lid went on; can be negative (a world younger than the batch, or a check's aging). */
    private long sealedAt;
    /** The overworld clock when the lid went on. */
    private long sealedClock;
    private int time;

    public FermentingCrockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FERMENTING_CROCK.get(), pos, state);
    }

    public boolean sealed() {
        return sealed;
    }

    public List<ItemStack> contents() {
        return items;
    }

    public CookingInput input() {
        return new CookingInput(List.copyOf(items), ItemStack.EMPTY);
    }

    /** 0..1: how far the sealed batch has fermented. */
    public float progress(Level level) {
        if (!sealed()) return 0;
        return time <= 0 ? 1F : Math.min(1F, elapsed(level) / (float) time);
    }

    /** Ticks since sealing: the tick count, or the clock if it ran further (sleeping skips the clock ahead, not the ticks). */
    private long elapsed(Level level) {
        return Math.max(level.getGameTime() - sealedAt, Math.max(0, level.getOverworldClockTime() - sealedClock));
    }

    public boolean done(Level level) {
        return sealed() && progress(level) >= 1F;
    }

    /** Moves the sealing {@code ticks} into the past, as if that much time went by (for checks: a day is too long to wait). */
    public void age(long ticks) {
        if (sealed()) {
            sealedAt -= ticks;
            sealedClock -= ticks;
            setChanged();
        }
    }

    /** Moves only the clock's sealing time into the past, as a night slept through does (for checks). */
    public void ageClock(long ticks) {
        if (sealed()) {
            sealedClock -= ticks;
            setChanged();
        }
    }

    /** Ticks until a sealed crock is done. */
    public long ticksLeft(Level level) {
        return sealed() ? Math.max(0, time - elapsed(level)) : 0;
    }

    public Optional<RecipeHolder<FermentingRecipe>> recipe(ServerLevel level) {
        return level.getServer().getRecipeManager().getRecipeFor(ModRecipes.FERMENTING.get(), input(), level);
    }

    /** Whether some fermenting recipe uses {@code stack}: anything else stays out of the crock. */
    public static boolean ferments(ServerLevel level, ItemStack stack) {
        for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
            if (holder.value() instanceof FermentingRecipe r && r.ingredients().stream().anyMatch(i -> i.test(stack))) return true;
        }
        return false;
    }

    /** Puts as much of {@code stack} in as fits (merging with a matching stack first); returns how many went in. */
    public int insert(ItemStack stack) {
        if (sealed() || stack.isEmpty()) return 0;
        int before = stack.getCount();
        for (int i = 0; i < SLOTS && !stack.isEmpty(); i++) {
            ItemStack here = items.get(i);
            if (!here.isEmpty() && ItemStack.isSameItemSameComponents(here, stack)) {
                int moved = Math.min(stack.getCount(), here.getMaxStackSize() - here.getCount());
                here.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < SLOTS && !stack.isEmpty(); i++) {
            if (items.get(i).isEmpty()) items.set(i, stack.split(stack.getCount()));
        }
        if (stack.getCount() != before) changed();
        return before - stack.getCount();
    }

    /** Everything back out of an open crock. */
    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (!items.get(i).isEmpty()) out.add(items.set(i, ItemStack.EMPTY));
        }
        if (!out.isEmpty()) changed();
        return out;
    }

    /** How many times {@code recipe} runs on the crock's stacks (at most {@link #MAX_BATCHES}). */
    public int batches(FermentingRecipe recipe) {
        return ferment(recipe, new ArrayList<>());
    }

    /** Seals the lid on {@code recipe}; false (and the crock stays open) when the contents don't fit it. */
    public boolean seal(Level level, FermentingRecipe recipe) {
        if (sealed() || batches(recipe) == 0) return false;
        sealed = true;
        sealedAt = level.getGameTime();
        sealedClock = level.getOverworldClockTime();
        time = recipe.time();
        changed();
        return true;
    }

    /**
     * Lifts the lid on a finished crock: the products, empties and spares, merged into stacks. If the recipe is gone
     * (a data pack changed), the ingredients come back as they went in.
     */
    public List<ItemStack> open(ServerLevel level) {
        List<ItemStack> out = finished(level);
        for (int i = 0; i < SLOTS; i++) items.set(i, ItemStack.EMPTY);
        sealed = false;
        sealedAt = 0;
        sealedClock = 0;
        time = 0;
        changed();
        return out;
    }

    /** What lifting the lid would give, without lifting it. */
    private List<ItemStack> finished(ServerLevel level) {
        List<ItemStack> out = new ArrayList<>();
        Optional<RecipeHolder<FermentingRecipe>> recipe = recipe(level);
        if (recipe.isEmpty() || ferment(recipe.get().value(), out) == 0) {
            out.clear();
            for (ItemStack s : items) if (!s.isEmpty()) out.add(s.copy());
        }
        return out;
    }

    /** Runs {@code recipe} on a copy of the stacks as often as it fits; fills {@code out}; returns the batch count. */
    private int ferment(FermentingRecipe recipe, List<ItemStack> out) {
        List<ItemStack> work = new ArrayList<>();
        for (ItemStack s : items) work.add(s.copy());
        List<ItemStack> made = new ArrayList<>();
        int batches = 0;
        while (batches < MAX_BATCHES) {
            int[] slots = recipe.slotsFor(new CookingInput(work, ItemStack.EMPTY));
            if (slots == null) break;
            for (int slot : slots) {
                ItemStack s = work.get(slot);
                ItemStackTemplate remainder = s.getItem().getCraftingRemainder();
                s.shrink(1);
                if (remainder != null) made.add(remainder.create());
            }
            made.add(recipe.result().create());
            batches++;
        }
        for (ItemStack s : work) if (!s.isEmpty()) made.add(s);
        for (ItemStack s : made) merge(out, s);
        return batches;
    }

    private static void merge(List<ItemStack> into, ItemStack stack) {
        ItemStack add = stack.copy();
        for (ItemStack o : into) {
            if (add.isEmpty()) return;
            if (ItemStack.isSameItemSameComponents(o, add)) {
                int moved = Math.min(add.getCount(), o.getMaxStackSize() - o.getCount());
                o.grow(moved);
                add.shrink(moved);
            }
        }
        if (!add.isEmpty()) into.add(add);
    }

    /** The lid's look for the block state: 0 open, 1 just sealed, 2 past halfway, 3 ready. */
    public int stage(Level level) {
        if (!sealed()) return 0;
        float p = progress(level);
        return p >= 1F ? 3 : p >= 0.5F ? 2 : 1;
    }

    /** Every second, the lid follows the fermenting. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, FermentingCrockBlockEntity crock) {
        if (level.getGameTime() % 20 == 0) crock.updateStage(level, pos, state);
    }

    private void updateStage(Level level, BlockPos pos, BlockState state) {
        int stage = stage(level);
        if (state.getValue(FermentingCrockBlock.STAGE) != stage) {
            level.setBlock(pos, state.setValue(FermentingCrockBlock.STAGE, stage), Block.UPDATE_ALL);
        }
    }

    private void changed() {
        setChanged();
        if (level != null) updateStage(level, worldPosition, getBlockState());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putBoolean("sealed", sealed);
        out.putLong("sealed_at", sealedAt);
        out.putLong("sealed_clock", sealedClock);
        out.putInt("time", time);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        sealed = in.getBooleanOr("sealed", false);
        sealedAt = in.getLongOr("sealed_at", 0L);
        sealedClock = in.getLongOr("sealed_clock", 0L);
        time = in.getIntOr("time", 0);
    }

    /** A finished crock spills its products; an unfinished or open one its ingredients. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        List<ItemStack> spill = done(server) ? finished(server) : items.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
        for (ItemStack s : spill) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
    }
}
