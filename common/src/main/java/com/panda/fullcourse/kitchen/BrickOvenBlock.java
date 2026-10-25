package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Brick Oven: a brick base with a dome and a furnace-like menu. {@code lit} while fuel burns: the mouth glows, it
 * gives light, smokes from the chimney and crackles. Its shape is the base and the dome, so neighbours show their faces.
 */
public class BrickOvenBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(2, 10, 2, 14, 14, 14));

    public BrickOvenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrickOvenBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.BRICK_OVEN.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<BrickOvenBlockEntity>) BrickOvenBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BrickOvenBlockEntity oven) player.openMenu(oven);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    /** While lit: flames in the mouth, smoke from the chimney on top, the occasional crackle. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, ModSounds.OVEN_CRACKLE.get(), SoundSource.BLOCKS, 1F, 1F, false);
        }
        Direction facing = state.getValue(FACING);
        double side = (random.nextDouble() - 0.5) * 0.4;
        double fx = x + facing.getStepX() * 0.52 + (facing.getAxis() == Direction.Axis.Z ? side : 0);
        double fz = z + facing.getStepZ() * 0.52 + (facing.getAxis() == Direction.Axis.X ? side : 0);
        double fy = y + 0.2 + random.nextDouble() * 0.2;
        level.addParticle(ParticleTypes.SMOKE, fx, fy, fz, 0, 0, 0);
        level.addParticle(ParticleTypes.FLAME, fx, fy, fz, 0, 0, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + (random.nextDouble() - 0.5) * 0.2, y + 1.1,
                    z + (random.nextDouble() - 0.5) * 0.2, 0, 0.04, 0);
        }
    }
}
