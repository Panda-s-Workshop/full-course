package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.food.FoodAdvancements;
import com.panda.fullcourse.registry.ModSounds;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Cutting Board: use an item on it to lay the stack down (more of the same adds to it), use a knife to cut one
 * (hold to keep cutting), use an empty hand to take the stack back. Anything can lie on it; only items with a cutting
 * recipe can be cut.
 */
public class CuttingBoardBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** A board 14 x 1 x 10 px, long side across the facing. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(1, 0, 3, 15, 1, 13));

    public CuttingBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CuttingBoardBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity board)) return InteractionResult.PASS;
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(KitchenTags.KNIVES)) {
            if (board.item().isEmpty()) {
                if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.fullcourse.board.empty"));
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel server) cut(server, pos, board, stack, player, hand);
            return InteractionResult.SUCCESS;
        }
        ItemStack lying = board.item();
        if (!lying.isEmpty() && ItemStack.isSameItemSameComponents(lying, stack)) {
            // more of the same: added to the stack up to a full one
            int room = lying.getMaxStackSize() - lying.getCount();
            if (room > 0 && !level.isClientSide()) {
                board.setItem(lying.copyWithCount(lying.getCount() + stack.consumeAndReturn(Math.min(room, stack.getCount()), player).getCount()));
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        if (!lying.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            board.setItem(stack.consumeAndReturn(stack.getCount(), player));
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity board) || board.item().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ItemStack taken = board.take();
            player.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    /** One knife stroke: one item off the board, the recipe's result popped out on top, the knife one use worse. */
    static boolean cut(ServerLevel level, BlockPos pos, CuttingBoardBlockEntity board, ItemStack knife, Player player, InteractionHand hand) {
        var recipe = board.recipe(level);
        if (recipe.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.fullcourse.board.no_recipe", board.item().getHoverName())
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        ItemStack lying = board.item();
        ItemStackTemplate result = recipe.get().value().output();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, lying.getItem()), pos.getX() + 0.5, pos.getY() + 0.15, pos.getZ() + 0.5,
                6, 0.15, 0.05, 0.15, 0.05);
        lying.shrink(1);
        board.setItem(lying.isEmpty() ? ItemStack.EMPTY : lying);
        Block.popResourceFromFace(level, pos, Direction.UP, result.create());
        knife.hurtAndBreak(1, player, hand);
        if (player instanceof ServerPlayer sp) FoodAdvancements.award(sp, "cutting_board");
        level.playSound(null, pos, ModSounds.BOARD_CHOP.get(), SoundSource.BLOCKS, 1F, 1F + level.getRandom().nextFloat() * 0.13F);
        return true;
    }
}
