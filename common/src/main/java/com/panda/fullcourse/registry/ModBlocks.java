package com.panda.fullcourse.registry;

import com.google.gson.JsonObject;
import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.kitchen.BrickOvenBlock;
import com.panda.fullcourse.kitchen.CookingPotBlock;
import com.panda.fullcourse.kitchen.CuttingBoardBlock;
import com.panda.fullcourse.kitchen.EmberPepperCropBlock;
import com.panda.fullcourse.kitchen.FeastBlock;
import com.panda.fullcourse.kitchen.FermentingCrockBlock;
import com.panda.fullcourse.kitchen.OnionCropBlock;
import com.panda.fullcourse.kitchen.WildEmberPepperBlock;
import com.panda.fullcourse.kitchen.WildOnionBlock;
import com.panda.fullcourse.platform.registry.DeferredBlock;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FullCourse.MODID);

    public static final DeferredBlock<CuttingBoardBlock> CUTTING_BOARD = BLOCKS.registerBlock("cutting_board", CuttingBoardBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
    public static final DeferredBlock<CookingPotBlock> COOKING_POT = BLOCKS.registerBlock("cooking_pot", CookingPotBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<OnionCropBlock> ONIONS = BLOCKS.registerBlock("onions", OnionCropBlock::new,
            p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<WildOnionBlock> WILD_ONIONS = BLOCKS.registerBlock("wild_onions", WildOnionBlock::new,
            p -> p.mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)
                    .pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<BrickOvenBlock> BRICK_OVEN = BLOCKS.registerBlock("brick_oven", BrickOvenBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(2.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(BrickOvenBlock.LIT) ? 13 : 0));
    public static final DeferredBlock<FermentingCrockBlock> FERMENTING_CROCK = BLOCKS.registerBlock("fermenting_crock", FermentingCrockBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(1.25F).sound(SoundType.DECORATED_POT).noOcclusion());
    public static final DeferredBlock<EmberPepperCropBlock> EMBER_PEPPERS = BLOCKS.registerBlock("ember_peppers", EmberPepperCropBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).noCollision().randomTicks().instabreak().sound(SoundType.NETHER_WART)
                    .pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<WildEmberPepperBlock> WILD_EMBER_PEPPERS = BLOCKS.registerBlock("wild_ember_peppers", WildEmberPepperBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).noCollision().instabreak().sound(SoundType.ROOTS).offsetType(BlockBehaviour.OffsetType.XZ)
                    .pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<FeastBlock> ROAST_BANQUET = feast("roast_banquet", MapColor.COLOR_BROWN);
    public static final DeferredBlock<FeastBlock> HARVEST_PIE = feast("harvest_pie", MapColor.COLOR_ORANGE);
    public static final DeferredBlock<FeastBlock> GLOW_BERRY_TART = feast("glow_berry_tart", MapColor.COLOR_YELLOW);

    private ModBlocks() {}

    /** A feast, its servings and platter from the item table. */
    private static DeferredBlock<FeastBlock> feast(String id, MapColor color) {
        JsonObject e = ModItems.Table.entry(id);
        int servings = e.get("servings").getAsInt(), serving = e.get("serving").getAsInt();
        boolean platter = e.get("platter").getAsBoolean();
        return BLOCKS.registerBlock(id, p -> new FeastBlock(p, servings, serving, platter),
                p -> p.mapColor(color).strength(0.5F).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.POPPED));
    }
}
