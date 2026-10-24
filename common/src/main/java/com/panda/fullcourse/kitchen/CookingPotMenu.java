package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.food.FoodTags;
import com.panda.fullcourse.registry.ModMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Cooking Pot's menu: a 3 x 2 ingredient grid, the container slot, the dish, and the player's inventory. */
public class CookingPotMenu extends AbstractContainerMenu {
    /** Where the slots sit on the 176 x 166 screen (the background texture draws their frames there). */
    public static final int GRID_X = 30, GRID_Y = 17, CONTAINER_X = 92, CONTAINER_Y = 53, OUTPUT_X = 124, OUTPUT_Y = 26;
    private static final int POT_SLOTS = CookingPotBlockEntity.SIZE;

    private final Container pot;
    private final ContainerData data;

    /** The client's menu: the server fills it. */
    public CookingPotMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(POT_SLOTS), new SimpleContainerData(CookingPotBlockEntity.DATA_COUNT));
    }

    public CookingPotMenu(int id, Inventory inventory, Container pot, ContainerData data) {
        super(ModMenus.COOKING_POT.get(), id);
        checkContainerSize(pot, POT_SLOTS);
        checkContainerDataCount(data, CookingPotBlockEntity.DATA_COUNT);
        this.pot = pot;
        this.data = data;
        pot.startOpen(inventory.player);
        for (int i = 0; i < CookingPotBlockEntity.INGREDIENTS; i++) {
            addSlot(new Slot(pot, i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18));
        }
        addSlot(new Slot(pot, CookingPotBlockEntity.CONTAINER, CONTAINER_X, CONTAINER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(FoodTags.CONTAINERS);
            }
        });
        addSlot(new Slot(pot, CookingPotBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    /** 0..1: how far the current dish has cooked. */
    public float progress() {
        int time = data.get(CookingPotBlockEntity.DATA_TIME);
        return time <= 0 ? 0 : Math.min(1F, data.get(CookingPotBlockEntity.DATA_PROGRESS) / (float) time);
    }

    public boolean heated() {
        return data.get(CookingPotBlockEntity.DATA_HEATED) != 0;
    }

    public CookingPotBlockEntity.Status status() {
        CookingPotBlockEntity.Status[] all = CookingPotBlockEntity.Status.values();
        return all[Math.floorMod(data.get(CookingPotBlockEntity.DATA_STATUS), all.length)];
    }

    /** The container the found recipe is waiting for, or empty. */
    public ItemStack needs() {
        int id = data.get(CookingPotBlockEntity.DATA_NEEDS);
        return id < 0 ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.byId(id));
    }

    @Override
    public boolean stillValid(Player player) {
        return pot.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        pot.stopOpen(player);
    }

    /** Shift-click: pot slots go to the inventory; from the inventory, bowls and bottles go to the container slot, the rest to the grid. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int invStart = POT_SLOTS, invEnd = slots.size();
        if (index < POT_SLOTS) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
            if (index == CookingPotBlockEntity.OUTPUT) slot.onQuickCraft(stack, before);
        } else {
            boolean moved = stack.is(FoodTags.CONTAINERS)
                    && moveItemStackTo(stack, CookingPotBlockEntity.CONTAINER, CookingPotBlockEntity.CONTAINER + 1, false);
            if (!moved && !moveItemStackTo(stack, 0, CookingPotBlockEntity.INGREDIENTS, false)) {
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
