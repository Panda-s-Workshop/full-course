package com.panda.fullcourse.registry;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.kitchen.BrickOvenBlockEntity;
import com.panda.fullcourse.kitchen.CookingPotBlockEntity;
import com.panda.fullcourse.kitchen.CuttingBoardBlockEntity;
import com.panda.fullcourse.kitchen.FeastBlockEntity;
import com.panda.fullcourse.kitchen.FermentingCrockBlockEntity;
import com.panda.fullcourse.platform.registry.DeferredHolder;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FullCourse.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CuttingBoardBlockEntity>> CUTTING_BOARD = TYPES.register("cutting_board",
            () -> new BlockEntityType<>(CuttingBoardBlockEntity::new, Set.of(ModBlocks.CUTTING_BOARD.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CookingPotBlockEntity>> COOKING_POT = TYPES.register("cooking_pot",
            () -> new BlockEntityType<>(CookingPotBlockEntity::new, Set.of(ModBlocks.COOKING_POT.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrickOvenBlockEntity>> BRICK_OVEN = TYPES.register("brick_oven",
            () -> new BlockEntityType<>(BrickOvenBlockEntity::new, Set.of(ModBlocks.BRICK_OVEN.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FermentingCrockBlockEntity>> FERMENTING_CROCK = TYPES.register("fermenting_crock",
            () -> new BlockEntityType<>(FermentingCrockBlockEntity::new, Set.of(ModBlocks.FERMENTING_CROCK.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeastBlockEntity>> FEAST = TYPES.register("feast",
            () -> new BlockEntityType<>(FeastBlockEntity::new, Set.of(ModBlocks.ROAST_BANQUET.get(), ModBlocks.HARVEST_PIE.get(), ModBlocks.GLOW_BERRY_TART.get())));

    private ModBlockEntities() {}
}
