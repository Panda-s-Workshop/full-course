package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.registry.ModSounds;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A feast: placed on a solid block and served by using it, a tier-5 meal per serving for whoever takes it, one serving
 * per player from each feast placed (and one helping of a dish every five minutes, across feasts); a Queasy player is
 * turned away without spending a serving. The
 * banquet and the pie leave their empty platter after the last serving; the tart goes with its last quarter. Broken
 * untouched it comes back whole; broken started, it's lost, as a cake is.
 */
public class FeastBlock extends Block implements EntityBlock {
    public static final int MAX_SERVINGS = 8;
    /** One helping per player in this long: a feast is shared, not eaten by whoever holds the button. */
    public static final int HELPING_TICKS = 5 * 60 * 20;
    public static final IntegerProperty SERVINGS = IntegerProperty.create("servings", 0, MAX_SERVINGS);
    /** The side it's served from, toward whoever placed it: the pie is cut and the tart taken from there. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape PLATTER = Block.box(1, 0, 1, 15, 1, 15);
    /** Up to the banquet's drumstick tips. */
    private static final VoxelShape FULL_PLATTER = Block.box(1, 0, 1, 15, 8, 15);
    private static final VoxelShape TIN = Block.box(3, 0, 3, 13, 3, 13);

    private final int servings;
    private final int serving;
    private final boolean platter;

    /**
     * @param servings how many it serves when placed (1..8)
     * @param serving the HP each serving heals
     * @param platter whether the empty platter stays after the last serving
     */
    public FeastBlock(Properties properties, int servings, int serving, boolean platter) {
        super(properties);
        this.servings = Math.clamp(servings, 1, MAX_SERVINGS);
        this.serving = serving;
        this.platter = platter;
        registerDefaultState(stateDefinition.any().setValue(SERVINGS, this.servings).setValue(FACING, Direction.NORTH));
    }

    public int servings() {
        return servings;
    }

    /** The HP each serving heals. */
    public int serving() {
        return serving;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SERVINGS, FACING);
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
        if (!platter) return TIN;
        return state.getValue(SERVINGS) == 0 ? PLATTER : FULL_PLATTER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int left = Math.min(state.getValue(SERVINGS), servings);
        if (left == 0) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.fullcourse.feast.empty"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!player.canEat(false)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.SUCCESS;
        Component refused = refusal(server, asItem());
        FeastBlockEntity table = level.getBlockEntity(pos) instanceof FeastBlockEntity f ? f : null;
        if (refused == null && table != null && table.served(server.getUUID())) {
            // one serving each from a feast: the rest are for the others at the table
            refused = Component.translatable("message.fullcourse.feast.served", asItem().getName(new ItemStack(asItem())));
        }
        if (refused != null) {
            server.sendOverlayMessage(refused);
            return InteractionResult.SUCCESS;
        }
        if (table != null) table.serve(server.getUUID());
        if (FullCourseConfig.keepVanillaHunger()) server.getFoodData().eat(serving, 0.8F);
        FoodRules.onEatDish(server, asItem(), serving);
        level.playSound(null, pos, ModSounds.FEAST_SERVE.get(), SoundSource.PLAYERS, 1F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        level.gameEvent(player, GameEvent.EAT, pos);
        if (left > 1 || platter) {
            level.setBlockAndUpdate(pos, state.setValue(SERVINGS, left - 1));
        } else {
            level.removeBlock(pos, false);
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Why {@code player} can't take a serving of {@code dish} (a feast, cake, another mod's pie) now, or null: Queasy
     * (the serving would give no meal), or a helping of it or one of its servings within the last {@link #HELPING_TICKS},
     * so holding the use key can't clear the table for everyone else. On a client too, from the synced state, so a
     * cake or pie the server will refuse isn't bitten there first; null on a client of a server without Full Course.
     */
    public static Component refusal(Player player, Item dish) {
        if (!(player instanceof ServerPlayer) && !FoodProfiles.clientReady()) return null;
        Component name = dish.getName(new ItemStack(dish));
        if (player.hasEffect(MobEffects.HUNGER)) {
            return Component.translatable("message.fullcourse.feast.queasy", name).withStyle(ChatFormatting.GREEN);
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(dish);
        long now = player.level().getGameTime();
        for (Map.Entry<Identifier, Long> helping : Services.PLATFORM.foodState(player).helpings().entrySet()) {
            long since = now - helping.getValue();
            boolean same = helping.getKey().equals(id) || BuiltInRegistries.ITEM.get(helping.getKey())
                    .map(h -> (player instanceof ServerPlayer server ? FoodProfiles.dishOf(server.level().getServer(), h.value())
                            : FoodProfiles.clientDishOf(h.value())) == dish).orElse(false);
            if (same && since >= 0 && since < HELPING_TICKS) {
                return Component.translatable("message.fullcourse.feast.had_share", name, FoodText.duration((int) (HELPING_TICKS - since)));
            }
        }
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FeastBlockEntity(pos, state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
