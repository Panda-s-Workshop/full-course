package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModSounds;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Cooking Pot: opens its menu on use and cooks over a heat source below it. {@code cooking} is true while
 * a recipe is under way, for the steam and the bubbling.
 */
public class CookingPotBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty COOKING = BooleanProperty.create("cooking");
    /** The pot 12 x 9 x 12 px, its handles out to the sides along the facing's axis. */
    /** Bubbles bursting on a soup with no colour of its own: the soup texture's light orange. */
    private static final DustParticleOptions SIMMER = new DustParticleOptions(0xE8A253, 0.6F);
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(
            Shapes.or(Block.box(2, 0, 2, 14, 9, 14), Block.box(0, 6, 6, 16, 8, 10)));

    public CookingPotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COOKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COOKING);
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
        return new CookingPotBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.COOKING_POT.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<CookingPotBlockEntity>) CookingPotBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot) player.openMenu(pot);
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

    /** Bubbles in the soup's own colour, a third of the way to white so they show on it. */
    private static DustParticleOptions simmer(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) return SIMMER;
        int c = pot.soupColor();
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return new DustParticleOptions((r + (255 - r) / 3) << 16 | (g + (255 - g) / 3) << 8 | (b + (255 - b) / 3), 0.6F);
    }

    /** Steam over a heated pot; bubbles and a simmer while it cooks. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.6, z = pos.getZ() + 0.5;
        if (state.getValue(COOKING)) {
            if (random.nextInt(3) == 0) {
                level.addParticle(simmer(level, pos), x + (random.nextDouble() - 0.5) * 0.5, y - 0.02, z + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
            }
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, x + (random.nextDouble() - 0.5) * 0.4, y + 0.1, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
            }
            if (random.nextInt(8) == 0) {
                level.playLocalSound(x, y, z, ModSounds.POT_BUBBLE.get(), SoundSource.BLOCKS, 1F, 0.8F + random.nextFloat() * 0.4F, false);
            }
        } else if (random.nextInt(4) == 0 && CookingPotBlockEntity.heated(level, pos)) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, x + (random.nextDouble() - 0.5) * 0.4, y + 0.1, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
        }
    }
}
