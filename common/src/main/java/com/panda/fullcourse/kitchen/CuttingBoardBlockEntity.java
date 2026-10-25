package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The stack lying on a Cutting Board: saved, synced to clients (the renderer draws it), dropped when the board breaks. */
public class CuttingBoardBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;

    public CuttingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CUTTING_BOARD.get(), pos, state);
    }

    public ItemStack item() {
        return item;
    }

    public void setItem(ItemStack stack) {
        item = stack;
        changed();
    }

    /** Takes the whole stack off the board. */
    public ItemStack take() {
        ItemStack out = item;
        item = ItemStack.EMPTY;
        changed();
        return out;
    }

    /** The cutting recipe for what lies on the board, if any. */
    public Optional<RecipeHolder<CuttingRecipe>> recipe(ServerLevel level) {
        // a cut from another mod's board isn't cut again (minced beef into our minced meat would double it)
        if (item.isEmpty() || com.panda.fullcourse.food.FoodProfiles.isCut(level.getServer(), item.getItem())) return Optional.empty();
        return level.getServer().getRecipeManager().getRecipeFor(ModRecipes.CUTTING.get(), new SingleRecipeInput(item), level);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!item.isEmpty()) out.store("item", ItemStack.CODEC, item);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        item = in.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !item.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, item);
    }
}
