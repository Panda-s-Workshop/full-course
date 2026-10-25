package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.food.FoodTags;
import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModRecipes;
import com.panda.fullcourse.registry.ModSounds;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * The Brick Oven's contents and its baking: four ingredient slots, fuel, the dish and the empties, run like a furnace.
 * A matching recipe with room for its dish lights the next fuel (vanilla's burn times); while lit, baking advances,
 * and without heat it cools back as a furnace's does. The bottles and buckets ingredients leave go to a free
 * ingredient slot, else to the empties slot. Hoppers: from above into the ingredients, from the sides into the fuel,
 * from below the dish, the empties and an empty bucket from the fuel slot.
 */
public class BrickOvenBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int INGREDIENTS = BakingRecipe.MAX_INGREDIENTS;
    public static final int FUEL = INGREDIENTS;
    public static final int OUTPUT = INGREDIENTS + 1;
    public static final int EMPTIES = OUTPUT + 1;
    public static final int SIZE = EMPTIES + 1;
    private static final int[] TOP = {0, 1, 2, 3};
    private static final int[] SIDE = {FUEL};
    private static final int[] BOTTOM = {OUTPUT, EMPTIES, FUEL, 0, 1, 2, 3};
    /** Data slots for the menu: progress, the recipe's time, fuel left, the fuel's full burn, {@link Status}. */
    public static final int DATA_PROGRESS = 0, DATA_TIME = 1, DATA_LIT = 2, DATA_LIT_TOTAL = 3, DATA_STATUS = 4, DATA_COUNT = 5;

    /** What the oven is doing, or why it isn't baking, for the screen. */
    public enum Status { EMPTY, BAKING, NO_FUEL, OUTPUT_FULL, NO_RECIPE }
    /** How fast unheated baking falls back, per tick (vanilla's furnace cools at 2). */
    private static final int COOL = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int progress;
    private int time = BakingRecipe.DEFAULT_TIME;
    private int lit;
    private int litTotal;
    private Status status = Status.EMPTY;
    private ResourceKey<Recipe<?>> baking;
    private RecipeManager.CachedCheck<CookingInput, BakingRecipe> check;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TIME -> time;
                // data slots travel as shorts: a modded fuel's long burn is sent scaled down, keeping the fraction left
                case DATA_LIT -> litTotal > Short.MAX_VALUE ? (int) ((long) lit * Short.MAX_VALUE / litTotal) : lit;
                case DATA_LIT_TOTAL -> Math.min(litTotal, Short.MAX_VALUE);
                case DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_PROGRESS -> progress = value;
                case DATA_TIME -> time = value;
                case DATA_LIT -> lit = value;
                case DATA_LIT_TOTAL -> litTotal = value;
                case DATA_STATUS -> status = Status.values()[Math.floorMod(value, Status.values().length)];
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public BrickOvenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BRICK_OVEN.get(), pos, state);
    }

    /** Fuel by vanilla's rule: anything with a cooking-fuel component (coal, logs, lava buckets, modded fuels). */
    public static boolean isFuel(ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL);
    }

    private int burnTime(ServerLevel level, ItemStack stack) {
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, getLootContext(level), 0);
    }

    public CookingInput input() {
        return new CookingInput(List.copyOf(items.subList(0, INGREDIENTS)), ItemStack.EMPTY);
    }

    public Optional<RecipeHolder<BakingRecipe>> recipe(ServerLevel level) {
        if (check == null) check = RecipeManager.createCheck(ModRecipes.BAKING.get());
        return check.getRecipeFor(input(), level);
    }

    public int progress() {
        return progress;
    }

    public boolean lit() {
        return lit > 0;
    }

    public Status status() {
        return status;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrickOvenBlockEntity oven) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;
        if (oven.lit > 0) {
            oven.lit--;
            changed = true;
        }
        Optional<RecipeHolder<BakingRecipe>> found = oven.recipe(server);
        ResourceKey<Recipe<?>> id = found.map(RecipeHolder::id).orElse(null);
        if (!Objects.equals(id, oven.baking)) {
            // a new recipe (or none) starts over; after loading, the saved progress belongs to the first recipe found
            if (oven.baking != null || id == null) {
                if (oven.progress != 0) changed = true;
                oven.progress = 0;
            }
            oven.baking = id;
        }
        boolean canBake = found.isPresent() && oven.fits(found.get().value().result());
        if (canBake && oven.lit == 0) changed |= oven.lightFuel(server, pos);
        if (canBake && oven.lit > 0) {
            BakingRecipe recipe = found.get().value();
            oven.time = recipe.time();
            if (++oven.progress >= recipe.time()) {
                oven.progress = 0;
                oven.finish(server, pos, recipe);
            }
            changed = true;
        } else if (oven.lit == 0 && oven.progress > 0) {
            oven.progress = Math.max(0, oven.progress - COOL);
            changed = true;
        }
        Status status = found.isEmpty() ? (oven.input().ingredients().stream().allMatch(ItemStack::isEmpty) ? Status.EMPTY : Status.NO_RECIPE)
                : !canBake ? Status.OUTPUT_FULL : oven.lit > 0 ? Status.BAKING : Status.NO_FUEL;
        if (status != oven.status) {
            oven.status = status;
            changed = true;
        }
        boolean litNow = oven.lit > 0;
        if (state.getValue(BrickOvenBlock.LIT) != litNow) {
            level.setBlock(pos, state.setValue(BrickOvenBlock.LIT, litNow), Block.UPDATE_ALL);
        }
        if (changed) oven.setChanged();
    }

    /** Burns the next fuel item; a lava bucket leaves its bucket behind in the slot. */
    private boolean lightFuel(ServerLevel level, BlockPos pos) {
        ItemStack fuel = items.get(FUEL);
        if (!isFuel(fuel)) return false;
        int burn = burnTime(level, fuel);
        if (burn <= 0) return false;
        lit = litTotal = burn;
        ItemStackTemplate remainder = fuel.getItem().getCraftingRemainder();
        fuel.shrink(1);
        if (remainder != null) {
            if (fuel.isEmpty()) items.set(FUEL, remainder.create());
            else Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, remainder.create());
        }
        return true;
    }

    /** The output slot has room for one more dish. */
    private boolean fits(ItemStackTemplate result) {
        ItemStack out = items.get(OUTPUT);
        if (out.isEmpty()) return true;
        ItemStack made = result.create();
        return ItemStack.isSameItemSameComponents(out, made) && out.getCount() + made.getCount() <= out.getMaxStackSize();
    }

    /** Baked: use up one of each ingredient, leave what they leave behind, serve the dish. */
    private void finish(ServerLevel level, BlockPos pos, BakingRecipe recipe) {
        int[] slots = recipe.slotsFor(input());
        if (slots == null) return;
        for (int slot : slots) {
            ItemStack stack = items.get(slot);
            ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
            stack.shrink(1);
            if (remainder == null) continue;
            if (stack.isEmpty()) items.set(slot, remainder.create());
            else keep(level, pos, remainder.create());
        }
        ItemStack made = recipe.result().create();
        ItemStack out = items.get(OUTPUT);
        if (out.isEmpty()) items.set(OUTPUT, made);
        else out.grow(made.getCount());
        level.playSound(null, pos, ModSounds.OVEN_DONE.get(), SoundSource.BLOCKS, 1F, 1F);
        level.playSound(null, pos, ModSounds.OVEN_DONE_CRACKLE.get(), SoundSource.BLOCKS, 1F, 1F);
    }

    /** An empty from a stack that isn't used up: into a free (or matching) ingredient slot, where it's spare, else the empties slot. */
    private void keep(ServerLevel level, BlockPos pos, ItemStack empty) {
        for (int i = 0; i < INGREDIENTS; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) {
                items.set(i, empty);
                return;
            }
            if (ItemStack.isSameItemSameComponents(s, empty) && s.getCount() < s.getMaxStackSize()) {
                s.grow(1);
                return;
            }
        }
        ItemStack held = items.get(EMPTIES);
        if (held.isEmpty()) {
            items.set(EMPTIES, empty);
        } else if (ItemStack.isSameItemSameComponents(held, empty) && held.getCount() < held.getMaxStackSize()) {
            held.grow(1);
        } else {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, empty);
        }
    }

    // ---- container ------------------------------------------------------------------------------------------------

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.fullcourse.brick_oven");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new BrickOvenMenu(id, inventory, this, data);
    }

    /** Fuel takes fuel (and an empty bucket, as vanilla's furnace allows); the dish and empties slots take nothing. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == OUTPUT || slot == EMPTIES) return false;
        if (slot == FUEL) return isFuel(stack) || stack.is(Items.BUCKET) && items.get(FUEL).isEmpty();
        return true;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack) && !(slot == FUEL && stack.is(Items.BUCKET));
    }

    /** From below: the dish, an empty bucket left by lava, and empties (buckets, bottles) an ingredient left behind. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (side != Direction.DOWN) return false;
        if (slot == OUTPUT || slot == EMPTIES) return true;
        if (slot == FUEL) return stack.is(Items.BUCKET);
        return stack.is(FoodTags.CONTAINERS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("progress", progress);
        out.putInt("lit", lit);
        out.putInt("lit_total", litTotal);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        progress = in.getIntOr("progress", 0);
        lit = in.getIntOr("lit", 0);
        litTotal = in.getIntOr("lit_total", 0);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, items);
    }
}
