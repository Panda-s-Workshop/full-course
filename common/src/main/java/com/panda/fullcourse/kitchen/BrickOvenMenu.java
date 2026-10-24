package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Brick Oven's menu: a 2 x 2 ingredient grid, fuel, the dish, the empties, and the player's inventory. */
public class BrickOvenMenu extends AbstractContainerMenu {
    /** Where the slots sit on the 176 x 166 screen (the background texture draws their frames there). */
    public static final int GRID_X = 26, GRID_Y = 26, FUEL_X = 68, FUEL_Y = 53, OUTPUT_X = 124, OUTPUT_Y = 35, EMPTIES_X = 124, EMPTIES_Y = 60;
    private static final int OVEN_SLOTS = BrickOvenBlockEntity.SIZE;

    private final Container oven;
    private final ContainerData data;

    /** The client's menu: the server fills it. */
    public BrickOvenMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(OVEN_SLOTS), new SimpleContainerData(BrickOvenBlockEntity.DATA_COUNT));
    }

    public BrickOvenMenu(int id, Inventory inventory, Container oven, ContainerData data) {
        super(ModMenus.BRICK_OVEN.get(), id);
        checkContainerSize(oven, OVEN_SLOTS);
        checkContainerDataCount(data, BrickOvenBlockEntity.DATA_COUNT);
        this.oven = oven;
        this.data = data;
        oven.startOpen(inventory.player);
        for (int i = 0; i < BrickOvenBlockEntity.INGREDIENTS; i++) {
            addSlot(new Slot(oven, i, GRID_X + (i % 2) * 18, GRID_Y + (i / 2) * 18));
        }
        addSlot(new Slot(oven, BrickOvenBlockEntity.FUEL, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BrickOvenBlockEntity.isFuel(stack) || stack.is(Items.BUCKET);
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return stack.is(Items.BUCKET) ? 1 : super.getMaxStackSize(stack);
            }
        });
        addSlot(new Slot(oven, BrickOvenBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new Slot(oven, BrickOvenBlockEntity.EMPTIES, EMPTIES_X, EMPTIES_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    /** 0..1: how far the current dish has baked. */
    public float progress() {
        int time = data.get(BrickOvenBlockEntity.DATA_TIME);
        return time <= 0 ? 0 : Math.min(1F, data.get(BrickOvenBlockEntity.DATA_PROGRESS) / (float) time);
    }

    /** 0..1: how much of the burning fuel is left. */
    public float fuelLeft() {
        int total = data.get(BrickOvenBlockEntity.DATA_LIT_TOTAL);
        return total <= 0 ? 0 : Math.min(1F, data.get(BrickOvenBlockEntity.DATA_LIT) / (float) total);
    }

    public boolean lit() {
        return data.get(BrickOvenBlockEntity.DATA_LIT) > 0;
    }

    /** A data slot's value as it goes to the client (each travels as a short). */
    public int data(int index) {
        return data.get(index);
    }

    public BrickOvenBlockEntity.Status status() {
        var all = BrickOvenBlockEntity.Status.values();
        return all[Math.floorMod(data.get(BrickOvenBlockEntity.DATA_STATUS), all.length)];
    }

    @Override
    public boolean stillValid(Player player) {
        return oven.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        oven.stopOpen(player);
    }

    /**
     * Shift-click: oven slots go to the inventory; from the inventory, fuel goes to the fuel slot and nowhere else in the
     * oven (in the grid it would only block the recipe), the rest to the grid.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int invStart = OVEN_SLOTS, invEnd = slots.size();
        if (index < OVEN_SLOTS) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
            if (index == BrickOvenBlockEntity.OUTPUT) slot.onQuickCraft(stack, before);
        } else {
            boolean fuel = BrickOvenBlockEntity.isFuel(stack);
            boolean moved = fuel ? moveItemStackTo(stack, BrickOvenBlockEntity.FUEL, BrickOvenBlockEntity.FUEL + 1, false)
                    : moveItemStackTo(stack, 0, BrickOvenBlockEntity.INGREDIENTS, false);
            if (!moved) {
                int hotbar = invEnd - 9;
                if (index < hotbar ? !moveItemStackTo(stack, hotbar, invEnd, false) : !moveItemStackTo(stack, invStart, hotbar, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return before;
    }
}
