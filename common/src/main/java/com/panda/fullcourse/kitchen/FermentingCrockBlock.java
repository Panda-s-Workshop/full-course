package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Fermenting Crock: no menu. Use ingredients on it to put them in; use it empty-handed to seal the lid on
 * a recipe; after the recipe's time, use it again to lift the lid and take what fermented. Sneak-use an open crock to
 * take its contents back. The lid shows the stage: open, sealed, tied (past halfway), ajar (ready). Needs no fuel.
 */
public class FermentingCrockBlock extends Block implements EntityBlock {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public FermentingCrockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FermentingCrockBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FERMENTING_CROCK.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<FermentingCrockBlockEntity>) FermentingCrockBlockEntity::serverTick;
    }

    /**
     * Something that ferments goes in while the lid is off, and the crock says what sealing it now would make. An empty
     * hand (the game asks here first, even then) or a sealed crock answers as if used empty-handed.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof FermentingCrockBlockEntity crock) || crock.sealed()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!FermentingCrockBlockEntity.ferments(server, stack)) {
            level.playSound(null, pos, ModSounds.CROCK_REFUSE.get(), SoundSource.BLOCKS, 1F, 1F);
            player.sendOverlayMessage(Component.translatable("message.fullcourse.crock.not_fermentable", stack.getHoverName()));
            return InteractionResult.SUCCESS;
        }
        ItemStack moving = player.hasInfiniteMaterials() ? stack.copy() : stack;
        if (crock.insert(moving) == 0) {
            level.playSound(null, pos, ModSounds.CROCK_REFUSE.get(), SoundSource.BLOCKS, 1F, 1F);
            player.sendOverlayMessage(Component.translatable("message.fullcourse.crock.full"));
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, pos, ModSounds.CROCK_FILL.get(), SoundSource.BLOCKS, 1F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        crock.recipe(server).ifPresent(holder -> {
            FermentingRecipe r = holder.value();
            ItemStack made = r.result().create();
            player.sendOverlayMessage(Component.translatable("message.fullcourse.crock.ready_to_seal", crock.batches(r) * made.getCount(),
                    made.getHoverName()));
        });
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FermentingCrockBlockEntity crock)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (crock.sealed()) {
            if (crock.done(server)) {
                List<ItemStack> out = crock.open(server);
                for (ItemStack s : out) player.getInventory().placeItemBackInInventory(s, Prediction.SERVER_ONLY);
                level.playSound(null, pos, ModSounds.CROCK_OPEN.get(), SoundSource.BLOCKS, 1F, 1F);
            } else {
                int left = (int) Math.min(Integer.MAX_VALUE, crock.ticksLeft(server));
                player.sendOverlayMessage(Component.translatable("message.fullcourse.crock.fermenting",
                        Math.round(crock.progress(server) * 100), FoodText.duration(left)));
            }
            return InteractionResult.SUCCESS;
        }
        if (crock.contents().stream().allMatch(ItemStack::isEmpty)) return InteractionResult.PASS;
        if (player.isSecondaryUseActive()) {
            for (ItemStack s : crock.takeAll()) player.getInventory().placeItemBackInInventory(s, Prediction.SERVER_ONLY);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        var recipe = crock.recipe(server);
        if (recipe.isPresent() && crock.seal(level, recipe.get().value())) {
            FermentingRecipe r = recipe.get().value();
            int batches = crock.batches(r);
            ItemStack made = r.result().create();
            player.sendOverlayMessage(Component.translatable("message.fullcourse.crock.sealed", batches * made.getCount(), made.getHoverName(),
                    FoodText.duration(r.time())));
            level.playSound(null, pos, ModSounds.CROCK_SEAL.get(), SoundSource.BLOCKS, 1F, 1F);
        } else {
            player.sendOverlayMessage(Component.translatable("message.fullcourse.crock.no_recipe"));
        }
        return InteractionResult.SUCCESS;
    }

    /** A sealed crock bubbles now and then; a ready one more. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (stage == 0 || random.nextInt(stage == 3 ? 4 : 12) != 0) return;
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 0.9, z, 0, 0.01, 0);
        if (stage == 3 && random.nextInt(3) == 0) {
            level.playLocalSound(x, pos.getY() + 0.8, z, ModSounds.CROCK_BUBBLE.get(), SoundSource.BLOCKS, 1F, 1F, false);
        }
    }
}
