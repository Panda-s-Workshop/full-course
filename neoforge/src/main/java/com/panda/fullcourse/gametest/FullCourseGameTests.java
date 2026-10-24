package com.panda.fullcourse.gametest;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.kitchen.CookingPotBlockEntity;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.registry.ModBlocks;
import com.panda.fullcourse.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registration, data and behaviour checks that run on a GameTest server. */
public final class FullCourseGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, FullCourse.MODID);
    private static final Identifier ARENA = FullCourse.id("test_arena");
    /** Tests that need longer than 5 s: the hopper line cooks three broths' worth (10 s each) plus the transfers. */
    private static final java.util.Map<String, Integer> LONG_TESTS = java.util.Map.of("hopper_feeds_pot", 900, "hopper_feeds_oven", 1200);

    static {
        FUNCTIONS.register("mod_loaded", () -> FullCourseGameTests::modLoaded);
        FUNCTIONS.register("lang_coverage", () -> FullCourseGameTests::langCoverage);
        FUNCTIONS.register("hopper_feeds_pot", () -> FullCourseGameTests::hopperFeedsPot);
        FUNCTIONS.register("hopper_feeds_oven", () -> FullCourseGameTests::hopperFeedsOven);
        FoodGameTests.register(FUNCTIONS);
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FullCourseGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(FullCourse.id("default"));
        for (var entry : FUNCTIONS.getEntries()) {
            ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(Registries.TEST_FUNCTION, entry.getId());
            int ticks = LONG_TESTS.getOrDefault(entry.getId().getPath(), 100);
            event.registerTest(key.identifier(), new FunctionGameTestInstance(key, new TestData<>(env, ARENA, ticks, 0, true)));
        }
    }

    // ---- tests -------------------------------------------------------------------------------------------------

    /** The mod's registries are reachable: its creative tab is registered under its namespace. */
    private static void modLoaded(GameTestHelper helper) {
        if (!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(FullCourse.id("main"))) helper.fail("fullcourse:main tab is not registered");
        helper.succeed();
    }

    /** Every item, block and literal key the mod shows has an English name. */
    private static void langCoverage(GameTestHelper helper) {
        var lang = net.minecraft.locale.Language.getInstance();
        List<String> missing = new ArrayList<>();
        ModItems.ITEMS.getEntries().forEach(h -> {
            String key = h.get().getDescriptionId();
            if (!lang.has(key)) missing.add(key);
        });
        ModBlocks.BLOCKS.getEntries().forEach(h -> {
            String key = h.get().getDescriptionId();
            if (!lang.has(key)) missing.add(key);
        });
        List<String> keys = new ArrayList<>(List.of("itemGroup.fullcourse", "tooltip.fullcourse.remedy", "tooltip.fullcourse.raw",
                "tooltip.fullcourse.chain", "tooltip.fullcourse.shift", "tooltip.fullcourse.lasts", "tooltip.fullcourse.heal",
                "tooltip.fullcourse.score", "tooltip.fullcourse.score_top", "effect.minecraft.hunger", "container.fullcourse.cooking_pot",
                "message.fullcourse.board.no_recipe", "gui.fullcourse.pot.heated", "gui.fullcourse.pot.cold", "gui.fullcourse.pot.progress", "gui.fullcourse.pot.status.no_heat", "gui.fullcourse.pot.status.needs", "gui.fullcourse.pot.status.full",
                "gui.fullcourse.pot.status.no_recipe", "hint.fullcourse.made", "hint.fullcourse.served_in", "hint.fullcourse.or",
                "hint.fullcourse.tag.c.foods.raw_meat", "hint.fullcourse.tag.c.foods.raw_fish", "tooltip.fullcourse.shift_recipe",
                "container.fullcourse.brick_oven", "gui.fullcourse.oven.lit", "gui.fullcourse.oven.unlit", "gui.fullcourse.oven.progress",
                "message.fullcourse.crock.full", "message.fullcourse.crock.fermenting", "message.fullcourse.crock.sealed", "message.fullcourse.crock.no_recipe",
                "message.fullcourse.crock.not_fermentable", "message.fullcourse.crock.ready_to_seal", "gui.fullcourse.oven.status.no_fuel",
                "gui.fullcourse.oven.status.full", "gui.fullcourse.oven.status.no_recipe", "gui.fullcourse.oven.empties",
                "tooltip.fullcourse.cutting_board.use", "tooltip.fullcourse.cutting_board.craft", "tooltip.fullcourse.cooking_pot.use",
                "tooltip.fullcourse.cooking_pot.craft", "tooltip.fullcourse.brick_oven.use", "tooltip.fullcourse.brick_oven.craft",
                "tooltip.fullcourse.fermenting_crock.use", "tooltip.fullcourse.fermenting_crock.craft",
                "toast.fullcourse.hint.title", "toast.fullcourse.hint.body",
                "command.fullcourse.tier.not_food"));
        for (int i = 0; i <= 5; i++) keys.add("tier.fullcourse." + i);
        for (String station : List.of("craft", "fire", "board", "pot", "oven", "crock")) keys.add("station.fullcourse." + station);
        for (Family f : Family.values()) {
            keys.add("family.fullcourse." + f.key());
            keys.add("family.fullcourse." + f.key() + ".effect");
        }
        for (String key : keys) {
            if (!lang.has(key)) missing.add(key);
        }
        if (!missing.isEmpty()) helper.fail("missing English names: " + missing);
        helper.succeed();
    }

    /**
     * Real hoppers on NeoForge (whose hopper code goes through its item-handler capability, falling back to the pot's
     * WorldlyContainer): one above feeds a bone into the ingredients, one below takes the finished dish out.
     */
    /**
     * A working line with vanilla hoppers: a campfire, a hopper on it emptying into a chest, the pot on
     * that hopper, bones fed from above and bottles from the side. Two broths reach the chest: heat rose through the
     * hopper, the split bone stack didn't jam the pot, and the dish came out of the bottom while the pot was heated.
     */
    private static void hopperFeedsPot(GameTestHelper helper) {
        BlockPos fire = new BlockPos(1, 1, 1), out = fire.above(), pot = out.above(), top = pot.above();
        BlockPos chest = out.east(), side = pot.west();
        helper.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState());
        helper.setBlock(out, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(chest, Blocks.CHEST.defaultBlockState());
        helper.setBlock(pot, ModBlocks.COOKING_POT.get().defaultBlockState());
        helper.setBlock(top, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(side, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.getBlockEntity(top, HopperBlockEntity.class).setItem(0, new ItemStack(Items.BONE, 2));
        helper.getBlockEntity(side, HopperBlockEntity.class).setItem(0, new ItemStack(Items.GLASS_BOTTLE, 2));
        // a stack split over two slots, as hoppers leave it
        CookingPotBlockEntity potEntity = helper.getBlockEntity(pot, CookingPotBlockEntity.class);
        potEntity.setItem(1, new ItemStack(Items.BONE));
        helper.succeedWhen(() -> {
            var box = helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int broth = 0;
            for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).is(ModItems.item("broth"))) broth += box.getItem(i).getCount();
            helper.assertTrue(broth >= 2, "two broths should reach the chest, " + broth + " so far (pot " + potEntity.status() + ")");
        });
    }

    /**
     * A real oven line: a hopper above drops Chorus Souffle's four ingredients in, one at the side feeds coal, and the
     * hopper below carries the souffles and the bottles the cream and syrup leave to a chest. Nothing spills on top.
     */
    private static void hopperFeedsOven(GameTestHelper helper) {
        BlockPos out = new BlockPos(1, 1, 1), oven = out.above(), top = oven.above();
        BlockPos chest = out.east(), side = oven.west();
        helper.setBlock(out, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(chest, Blocks.CHEST.defaultBlockState());
        helper.setBlock(oven, ModBlocks.BRICK_OVEN.get().defaultBlockState());
        helper.setBlock(top, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(side, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        HopperBlockEntity feed = helper.getBlockEntity(top, HopperBlockEntity.class);
        feed.setItem(0, new ItemStack(ModItems.item("charred_chorus"), 2));
        feed.setItem(1, new ItemStack(Items.EGG, 2));
        feed.setItem(2, new ItemStack(ModItems.item("sweet_cream"), 2));
        feed.setItem(3, new ItemStack(ModItems.item("syrup"), 2));
        helper.getBlockEntity(side, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COAL, 2));
        helper.succeedWhen(() -> {
            var box = helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int souffles = 0, bottles = 0;
            for (int i = 0; i < box.getContainerSize(); i++) {
                if (box.getItem(i).is(ModItems.item("chorus_souffle"))) souffles += box.getItem(i).getCount();
                if (box.getItem(i).is(Items.GLASS_BOTTLE)) bottles += box.getItem(i).getCount();
            }
            helper.assertTrue(souffles == 2 && bottles == 4, "two souffles and four bottles should reach the chest: " + souffles + ", " + bottles);
            helper.assertItemEntityNotPresent(Items.GLASS_BOTTLE);
        });
    }
}
