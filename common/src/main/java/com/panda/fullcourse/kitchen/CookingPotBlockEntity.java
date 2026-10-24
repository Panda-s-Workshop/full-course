package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.food.FoodTags;
import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Cooking Pot's contents and its cooking: six ingredient slots, a container slot and the dish. Over heat,
 * a matching recipe cooks for its time; then one of each ingredient (and one container) is used up, what an ingredient
 * leaves behind (a bucket, a bottle) stays in its slot or pops out, and the dish lands in the output slot.
 * Hoppers: from above into the ingredients, from the sides into the container slot (bowls, bottles, buckets only), from
 * below out of the output (and the empties left in ingredient slots). Heat rises through a hopper
 * ({@code #fullcourse:heat_conductors}), so a pot on a hopper on a campfire cooks and empties itself.
 */
public class CookingPotBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int INGREDIENTS = CookingRecipe.MAX_INGREDIENTS;
    public static final int CONTAINER = INGREDIENTS;
    public static final int OUTPUT = INGREDIENTS + 1;
    public static final int SIZE = OUTPUT + 1;
    private static final int[] TOP = {0, 1, 2, 3, 4, 5};
    private static final int[] SIDE = {CONTAINER};
    private static final int[] BOTTOM = {OUTPUT, 0, 1, 2, 3, 4, 5};
    /** Data slots for the menu: progress, the recipe's time, heated (0/1), {@link Status}, the container a dish needs. */
    public static final int DATA_PROGRESS = 0, DATA_TIME = 1, DATA_HEATED = 2, DATA_STATUS = 3, DATA_NEEDS = 4, DATA_COUNT = 5;

    /** What the pot is doing, for its screen: the reason it isn't cooking, if it isn't. */
    public enum Status { EMPTY, COOKING, NO_HEAT, NEEDS_CONTAINER, OUTPUT_FULL, NO_RECIPE }

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int progress;
    private int time = CookingRecipe.DEFAULT_TIME;
    private boolean heated;
    private Status status = Status.EMPTY;
    /** The raw item id of the container the found recipe wants, when it's missing. */
    private int needs = -1;
    private ResourceKey<Recipe<?>> cooking;
    private RecipeManager.CachedCheck<CookingInput, CookingRecipe> check;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TIME -> time;
                case DATA_HEATED -> heated ? 1 : 0;
                case DATA_STATUS -> status.ordinal();
                case DATA_NEEDS -> needs;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_PROGRESS -> progress = value;
                case DATA_TIME -> time = value;
                case DATA_HEATED -> heated = value != 0;
                case DATA_STATUS -> status = Status.values()[Math.floorMod(value, Status.values().length)];
                case DATA_NEEDS -> needs = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CookingPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOKING_POT.get(), pos, state);
    }

    /**
     * Heat comes from the block below: anything in {@code #fullcourse:heat_sources} while it burns ({@link KitchenHeat}:
     * its block entity's own say, a lit state, or always). It rises through one {@code #fullcourse:heat_conductors}
     * block (a hopper) to the pot above it.
     */
    public static boolean heated(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).is(KitchenTags.HEAT_CONDUCTORS)) below = below.below();
        BlockState heat = level.getBlockState(below);
        return heat.is(KitchenTags.HEAT_SOURCES) && KitchenHeat.burning(heat, level.getBlockEntity(below));
    }

    public CookingInput input() {
        return new CookingInput(List.copyOf(items.subList(0, INGREDIENTS)), items.get(CONTAINER));
    }

    public Optional<RecipeHolder<CookingRecipe>> recipe(ServerLevel level) {
        if (check == null) check = RecipeManager.createCheck(ModRecipes.COOKING.get());
        return check.getRecipeFor(input(), level);
    }

    public int progress() {
        return progress;
    }

    public Status status() {
        return status;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot) {
        if (!(level instanceof ServerLevel server)) return;
        pot.heated = heated(level, pos);
        Optional<RecipeHolder<CookingRecipe>> found = pot.recipe(server);
        boolean cooking = false;
        Status status = pot.input().ingredients().stream().allMatch(ItemStack::isEmpty) ? Status.EMPTY : Status.NO_RECIPE;
        int needs = -1;
        ResourceKey<Recipe<?>> id = found.map(RecipeHolder::id).orElse(null);
        if (!Objects.equals(id, pot.cooking)) {
            // a new recipe (or none) starts over; after loading, the saved progress belongs to the first recipe found
            if (pot.cooking != null || id == null) {
                if (pot.progress != 0) pot.setChanged();
                pot.progress = 0;
            }
            pot.cooking = id;
        }
        if (found.isPresent()) {
            CookingRecipe recipe = found.get().value();
            pot.time = recipe.time();
            boolean served = recipe.container().map(cont -> cont.test(pot.items.get(CONTAINER))).orElse(true);
            if (!served) {
                status = Status.NEEDS_CONTAINER;
                needs = recipe.container().flatMap(cont -> cont.items().findFirst())
                        .map(h -> BuiltInRegistries.ITEM.getId(h.value())).orElse(-1);
            } else if (!pot.fits(recipe.result())) {
                status = Status.OUTPUT_FULL;
            } else if (!pot.heated) {
                status = Status.NO_HEAT;
            } else {
                status = Status.COOKING;
                cooking = true;
                if (++pot.progress >= recipe.time()) {
                    pot.progress = 0;
                    pot.finish(server, pos, recipe);
                }
                pot.setChanged();
            }
        }
        pot.status = status;
        pot.needs = needs;
        if (state.getValue(CookingPotBlock.COOKING) != cooking) {
            level.setBlock(pos, state.setValue(CookingPotBlock.COOKING, cooking), Block.UPDATE_ALL);
        }
    }

    /** The output slot has room for one more serving. */
    private boolean fits(ItemStackTemplate result) {
        ItemStack out = items.get(OUTPUT);
        if (out.isEmpty()) return true;
        ItemStack made = result.create();
        return ItemStack.isSameItemSameComponents(out, made) && out.getCount() + made.getCount() <= out.getMaxStackSize();
    }

    /** The recipe is done: use up one of each ingredient and a container, leave what they leave behind, serve the dish. */
    private void finish(ServerLevel level, BlockPos pos, CookingRecipe recipe) {
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
        if (recipe.container().isPresent()) items.get(CONTAINER).shrink(1);
        ItemStack made = recipe.result().create();
        ItemStack out = items.get(OUTPUT);
        if (out.isEmpty()) items.set(OUTPUT, made);
        else out.grow(made.getCount());
        level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.0F);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 1.6F);
    }

    /**
     * Where an empty goes when its ingredient's stack isn't used up: onto the container slot's stack if it's the same,
     * else into a free ingredient slot (empties there don't block a recipe), else out on top of the pot.
     */
    private void keep(ServerLevel level, BlockPos pos, ItemStack empty) {
        ItemStack cont = items.get(CONTAINER);
        if (ItemStack.isSameItemSameComponents(cont, empty) && cont.getCount() < cont.getMaxStackSize()) {
            cont.grow(1);
            return;
        }
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
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, empty);
    }

    // ---- container ------------------------------------------------------------------------------------------------

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.fullcourse.cooking_pot");
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
        return new CookingPotMenu(id, inventory, this, data);
    }

    /** The container slot holds containers (bowls, bottles, buckets); the output slot takes nothing. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == CONTAINER) return stack.is(FoodTags.CONTAINERS);
        return slot != OUTPUT;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    /** From below: the dish, and empties (buckets, bottles) an ingredient left behind. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (side != Direction.DOWN) return false;
        return slot == OUTPUT || (slot < INGREDIENTS && stack.is(FoodTags.CONTAINERS));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        progress = in.getIntOr("progress", 0);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, items);
    }
}
