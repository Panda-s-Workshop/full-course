package com.panda.fullcourse.checks;

import static com.panda.fullcourse.checks.KitchenChecks.click;
import static com.panda.fullcourse.checks.KitchenChecks.dropsAround;
import static com.panda.fullcourse.checks.KitchenChecks.with;
import static com.panda.fullcourse.checks.RuleChecks.check;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.kitchen.BrickOvenBlock;
import com.panda.fullcourse.kitchen.BrickOvenBlockEntity;
import com.panda.fullcourse.kitchen.BrickOvenMenu;
import com.panda.fullcourse.kitchen.FermentingCrockBlock;
import com.panda.fullcourse.kitchen.FermentingCrockBlockEntity;
import com.panda.fullcourse.kitchen.FermentingRecipe;
import com.panda.fullcourse.registry.ModBlocks;
import com.panda.fullcourse.registry.ModItems;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/** The Brick Oven, the Fermenting Crock and Ember Peppers. Shared by both loaders. */
final class StationChecks {
    private StationChecks() {}

    static void register(Map<String, Consumer<RuleChecks.Ctx>> checks) {
        checks.put("oven_needs_fuel", StationChecks::ovenNeedsFuel);
        checks.put("oven_bakes_pie", StationChecks::ovenBakesPie);
        checks.put("oven_lava_leaves_bucket", StationChecks::ovenLavaLeavesBucket);
        checks.put("oven_hopper_faces", StationChecks::ovenHopperFaces);
        checks.put("oven_says_why", StationChecks::ovenSaysWhy);
        checks.put("oven_keeps_empties", StationChecks::ovenKeepsEmpties);
        checks.put("oven_shift_click_fuel", StationChecks::ovenShiftClickFuel);
        checks.put("oven_long_fuel_fits_menu", StationChecks::ovenLongFuelFitsMenu);
        checks.put("crock_ferments_after_time", StationChecks::crockFermentsAfterTime);
        checks.put("crock_runs_batches", StationChecks::crockRunsBatches);
        checks.put("crock_broken_early_gives_ingredients", StationChecks::crockBrokenEarly);
        checks.put("crock_by_hand", StationChecks::crockByHand);
        checks.put("crock_counts_a_night_slept", StationChecks::crockCountsANightSlept);
        checks.put("ember_pepper_needs_soul_soil", StationChecks::emberPepperNeedsSoulSoil);
        checks.put("ember_patch_places", StationChecks::emberPatchPlaces);
        checks.put("wild_ember_peppers_in_crimson_forest", StationChecks::wildEmberPeppersInCrimsonForest);
        checks.put("apple_pie_chain", StationChecks::applePieChain);
    }

    private static Item item(String path) {
        return ModItems.item(path);
    }

    // ---- oven ---------------------------------------------------------------------------------------------------

    private static List<ItemStack> applePie() {
        return List.of(new ItemStack(Items.APPLE), new ItemStack(Items.SUGAR), new ItemStack(item("pastry")));
    }

    /** An oven with copies of these ingredients and this fuel, ticked {@code ticks} times. */
    private static void oven(RuleChecks.Ctx c, List<ItemStack> ingredients, ItemStack fuel, int ticks, Consumer<BrickOvenBlockEntity> after) {
        BlockPos pos = c.spot();
        with(c, pos, ModBlocks.BRICK_OVEN.get().defaultBlockState(), () -> {
            BrickOvenBlockEntity oven = (BrickOvenBlockEntity) c.level().getBlockEntity(pos);
            for (int i = 0; i < ingredients.size(); i++) oven.setItem(i, ingredients.get(i).copy());
            oven.setItem(BrickOvenBlockEntity.FUEL, fuel.copy());
            for (int i = 0; i < ticks; i++) BrickOvenBlockEntity.serverTick(c.level(), pos, c.level().getBlockState(pos), oven);
            after.accept(oven);
        });
    }

    private static void ovenNeedsFuel(RuleChecks.Ctx c) {
        oven(c, applePie(), ItemStack.EMPTY, 400, oven -> {
            check(oven.getItem(BrickOvenBlockEntity.OUTPUT).isEmpty() && oven.progress() == 0 && !oven.lit(), "no fuel, no pie");
            check(!c.level().getBlockState(c.spot()).getValue(BrickOvenBlock.LIT), "an unfuelled oven isn't lit");
        });
        // fuel but nothing to bake: the oven doesn't burn it
        oven(c, List.of(), new ItemStack(Items.COAL, 3), 20, oven ->
                check(oven.getItem(BrickOvenBlockEntity.FUEL).getCount() == 3 && !oven.lit(), "nothing to bake, no coal burned"));
    }

    private static void ovenBakesPie(RuleChecks.Ctx c) {
        oven(c, applePie(), new ItemStack(Items.COAL, 4), 299, oven -> {
            check(oven.getItem(BrickOvenBlockEntity.OUTPUT).isEmpty() && oven.progress() == 299, "still baking at 299 ticks: %s", oven.progress());
            check(c.level().getBlockState(c.spot()).getValue(BrickOvenBlock.LIT), "a burning oven is lit");
        });
        oven(c, applePie(), new ItemStack(Items.COAL, 4), 300, oven -> {
            check(oven.getItem(BrickOvenBlockEntity.OUTPUT).is(item("apple_pie")), "apple pie in 15 s: %s", oven.getItem(BrickOvenBlockEntity.OUTPUT));
            check(oven.getItem(BrickOvenBlockEntity.FUEL).getCount() == 3, "one coal lit: %s", oven.getItem(BrickOvenBlockEntity.FUEL));
            check(oven.getItem(0).isEmpty() && oven.getItem(1).isEmpty() && oven.getItem(2).isEmpty(), "the ingredients are used up");
        });
    }

    private static void ovenLavaLeavesBucket(RuleChecks.Ctx c) {
        oven(c, applePie(), new ItemStack(Items.LAVA_BUCKET), 1, oven -> {
            check(oven.lit() && oven.getItem(BrickOvenBlockEntity.FUEL).is(Items.BUCKET), "lava burns and leaves its bucket: %s",
                    oven.getItem(BrickOvenBlockEntity.FUEL));
            check(oven.canTakeItemThroughFace(BrickOvenBlockEntity.FUEL, oven.getItem(BrickOvenBlockEntity.FUEL), Direction.DOWN),
                    "the empty bucket comes out of the bottom");
        });
    }

    /** What hoppers do (vanilla's own transfer): ingredients from above, fuel from the sides, the dish from below. */
    private static void ovenHopperFaces(RuleChecks.Ctx c) {
        oven(c, List.of(), ItemStack.EMPTY, 0, oven -> {
            ItemStack left = HopperBlockEntity.addItem(null, oven, new ItemStack(Items.APPLE), Direction.UP);
            check(left.isEmpty() && oven.getItem(0).is(Items.APPLE), "from above: into the ingredients");
            left = HopperBlockEntity.addItem(null, oven, new ItemStack(Items.SUGAR), Direction.NORTH);
            check(!left.isEmpty() && oven.getItem(BrickOvenBlockEntity.FUEL).isEmpty(), "sugar isn't fuel: it stays in the hopper");
            left = HopperBlockEntity.addItem(null, oven, new ItemStack(Items.COAL), Direction.NORTH);
            check(left.isEmpty() && oven.getItem(BrickOvenBlockEntity.FUEL).is(Items.COAL), "from the side: coal into the fuel slot");
            oven.setItem(BrickOvenBlockEntity.OUTPUT, new ItemStack(item("apple_pie")));
            check(oven.canTakeItemThroughFace(BrickOvenBlockEntity.OUTPUT, oven.getItem(BrickOvenBlockEntity.OUTPUT), Direction.DOWN)
                    && !oven.canTakeItemThroughFace(0, oven.getItem(0), Direction.DOWN)
                    && !oven.canTakeItemThroughFace(BrickOvenBlockEntity.FUEL, oven.getItem(BrickOvenBlockEntity.FUEL), Direction.DOWN),
                    "only the dish (and empties) out of the bottom");
        });
    }

    /** The status line: why an oven isn't baking. */
    private static void ovenSaysWhy(RuleChecks.Ctx c) {
        oven(c, List.of(), new ItemStack(Items.COAL), 1, oven -> check(oven.status() == BrickOvenBlockEntity.Status.EMPTY, "empty: %s", oven.status()));
        oven(c, List.of(new ItemStack(Items.APPLE)), new ItemStack(Items.COAL), 1, oven ->
                check(oven.status() == BrickOvenBlockEntity.Status.NO_RECIPE && !oven.lit(), "an apple alone bakes nothing: %s", oven.status()));
        oven(c, applePie(), ItemStack.EMPTY, 1, oven -> check(oven.status() == BrickOvenBlockEntity.Status.NO_FUEL, "no fuel: %s", oven.status()));
        oven(c, applePie(), new ItemStack(Items.COAL), 1, oven -> check(oven.status() == BrickOvenBlockEntity.Status.BAKING, "baking: %s", oven.status()));
        ItemStack pies = new ItemStack(item("apple_pie"));
        pies.setCount(pies.getMaxStackSize());
        BlockPos pos = c.spot();
        with(c, pos, ModBlocks.BRICK_OVEN.get().defaultBlockState(), () -> {
            BrickOvenBlockEntity oven = (BrickOvenBlockEntity) c.level().getBlockEntity(pos);
            List<ItemStack> in = applePie();
            for (int i = 0; i < in.size(); i++) oven.setItem(i, in.get(i));
            oven.setItem(BrickOvenBlockEntity.FUEL, new ItemStack(Items.COAL));
            oven.setItem(BrickOvenBlockEntity.OUTPUT, pies);
            BrickOvenBlockEntity.serverTick(c.level(), pos, c.level().getBlockState(pos), oven);
            check(oven.status() == BrickOvenBlockEntity.Status.OUTPUT_FULL && !oven.lit(), "a full output stops it, no coal burned: %s", oven.status());
        });
    }

    /**
     * Chorus Souffle fills all four slots and two of them leave a bottle: the first bake's bottles go to the empties
     * slot (no slot is free yet), the second's to the slots it used up. Nothing lands on top of the oven.
     */
    private static void ovenKeepsEmpties(RuleChecks.Ctx c) {
        List<ItemStack> souffle = List.of(new ItemStack(item("charred_chorus"), 2), new ItemStack(Items.EGG, 2),
                new ItemStack(item("sweet_cream"), 2), new ItemStack(item("syrup"), 2));
        oven(c, souffle, new ItemStack(Items.COAL, 2), 300, oven -> {
            check(oven.getItem(BrickOvenBlockEntity.OUTPUT).is(item("chorus_souffle")), "one souffle: %s", oven.getItem(BrickOvenBlockEntity.OUTPUT));
            check(oven.getItem(BrickOvenBlockEntity.EMPTIES).is(Items.GLASS_BOTTLE) && oven.getItem(BrickOvenBlockEntity.EMPTIES).getCount() == 2,
                    "the two bottles in the empties slot: %s", oven.getItem(BrickOvenBlockEntity.EMPTIES));
            check(dropsAround(c.level(), c.spot()).isEmpty(), "nothing dropped on the oven");
            check(oven.canTakeItemThroughFace(BrickOvenBlockEntity.EMPTIES, oven.getItem(BrickOvenBlockEntity.EMPTIES), Direction.DOWN),
                    "a hopper below takes the empties");
            check(!oven.canPlaceItem(BrickOvenBlockEntity.EMPTIES, new ItemStack(Items.GLASS_BOTTLE)), "nothing goes into the empties slot");
        });
        oven(c, souffle, new ItemStack(Items.COAL, 2), 600, oven -> {
            int bottles = 0;
            for (int i = 0; i < BrickOvenBlockEntity.SIZE; i++) if (oven.getItem(i).is(Items.GLASS_BOTTLE)) bottles += oven.getItem(i).getCount();
            check(oven.getItem(BrickOvenBlockEntity.OUTPUT).getCount() == 2 && bottles == 4, "two souffles, four bottles kept: %s", bottles);
            check(dropsAround(c.level(), c.spot()).isEmpty(), "still nothing dropped");
        });
    }

    /**
     * Shift-clicking fuel goes to the fuel slot, or (when it's full) between inventory and hotbar as vanilla's furnace
     * does, never into the grid: there it would only block every recipe.
     */
    private static void ovenShiftClickFuel(RuleChecks.Ctx c) {
        var inv = c.player().getInventory();
        ItemStack before = inv.getItem(9).copy();
        int coal = inv.countItem(Items.COAL);
        oven(c, List.of(), new ItemStack(Items.COAL, 64), 0, oven -> {
            BrickOvenMenu menu = (BrickOvenMenu) oven.createMenu(0, inv, c.player());
            try {
                inv.setItem(9, new ItemStack(Items.COAL, 16));
                menu.quickMoveStack(c.player(), BrickOvenBlockEntity.SIZE);
                boolean gridEmpty = true;
                for (int i = 0; i < BrickOvenBlockEntity.INGREDIENTS; i++) gridEmpty &= oven.getItem(i).isEmpty();
                check(gridEmpty && inv.countItem(Items.COAL) == coal + 16, "coal stays in the inventory when the fuel slot is full");
                take(inv, Items.COAL, 16);
                inv.setItem(9, new ItemStack(Items.COAL, 16));
                oven.setItem(BrickOvenBlockEntity.FUEL, ItemStack.EMPTY);
                menu.quickMoveStack(c.player(), BrickOvenBlockEntity.SIZE);
                check(oven.getItem(BrickOvenBlockEntity.FUEL).getCount() == 16, "and goes to the fuel slot when there's room");
                inv.setItem(9, new ItemStack(Items.APPLE, 3));
                menu.quickMoveStack(c.player(), BrickOvenBlockEntity.SIZE);
                check(oven.getItem(0).is(Items.APPLE), "an ingredient goes to the grid");
            } finally {
                menu.removed(c.player());
                inv.setItem(9, before);
            }
        });
    }

    /** Data slots travel as shorts: a modded fuel burning longer than 32 767 ticks is sent scaled, keeping the fraction. */
    private static void ovenLongFuelFitsMenu(RuleChecks.Ctx c) {
        oven(c, List.of(), ItemStack.EMPTY, 0, oven -> {
            BrickOvenMenu menu = (BrickOvenMenu) oven.createMenu(0, c.player().getInventory(), c.player());
            try {
                menu.setData(BrickOvenBlockEntity.DATA_LIT_TOTAL, 100_000);
                menu.setData(BrickOvenBlockEntity.DATA_LIT, 25_000);
                int lit = menu.data(BrickOvenBlockEntity.DATA_LIT), total = menu.data(BrickOvenBlockEntity.DATA_LIT_TOTAL);
                check(total <= Short.MAX_VALUE && lit <= Short.MAX_VALUE, "both fit a short: %s / %s", lit, total);
                check(Math.abs(lit / (float) total - 0.25F) < 0.001F && menu.lit(), "a quarter left, still lit: %s / %s", lit, total);
            } finally {
                menu.removed(c.player());
            }
        });
    }

    // ---- crock --------------------------------------------------------------------------------------------------

    private static void crock(RuleChecks.Ctx c, Consumer<FermentingCrockBlockEntity> body) {
        BlockPos pos = c.spot();
        with(c, pos, ModBlocks.FERMENTING_CROCK.get().defaultBlockState(), () -> body.accept((FermentingCrockBlockEntity) c.level().getBlockEntity(pos)));
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void crockFermentsAfterTime(RuleChecks.Ctx c) {
        crock(c, crock -> {
            crock.insert(new ItemStack(Items.MILK_BUCKET));
            var recipe = crock.recipe(c.level());
            check(recipe.isPresent() && crock.seal(c.level(), recipe.get().value()), "milk seals as butter");
            check(crock.stage(c.level()) == 1 && crock.insert(new ItemStack(Items.SUGAR)) == 0, "sealed: stage 1, nothing more goes in");
            crock.age(5999);
            check(crock.stage(c.level()) == 1 && !crock.done(c.level()), "not halfway at 5999 ticks: stage %s, progress %s, %s ticks left",
                    crock.stage(c.level()), crock.progress(c.level()), crock.ticksLeft(c.level()));
            crock.age(1);
            check(crock.stage(c.level()) == 2, "tied past halfway");
            crock.age(5999);
            check(!crock.done(c.level()), "not done at 11 999 ticks (half a day is 12 000)");
            crock.age(1);
            check(crock.done(c.level()) && crock.stage(c.level()) == 3, "ready at 12 000 ticks");
            List<ItemStack> out = crock.open(c.level());
            check(count(out, item("butter")) == 3 && count(out, Items.BUCKET) == 1 && out.size() == 2, "3 butter and the bucket: %s", out);
            check(!crock.sealed() && crock.stage(c.level()) == 0, "open again");
        });
    }

    private static void crockRunsBatches(RuleChecks.Ctx c) {
        crock(c, crock -> {
            // sweet cream: milk + sugar + 2 bottles; one bucket of milk, so one batch, the rest comes back
            crock.insert(new ItemStack(Items.MILK_BUCKET));
            crock.insert(new ItemStack(Items.SUGAR, 8));
            crock.insert(new ItemStack(Items.GLASS_BOTTLE, 8));
            var recipe = crock.recipe(c.level()).orElseThrow().value();
            check(crock.batches(recipe) == 1, "one bucket, one batch: %s", crock.batches(recipe));
            crock.seal(c.level(), recipe);
            crock.age(recipe.time());
            List<ItemStack> out = crock.open(c.level());
            check(count(out, item("sweet_cream")) == 2 && count(out, Items.SUGAR) == 7 && count(out, Items.GLASS_BOTTLE) == 6
                    && count(out, Items.BUCKET) == 1, "2 sweet cream, 7 sugar, 6 bottles, a bucket: %s", out);
        });
        crock(c, crock -> {
            // four buckets of milk in four slots are one pool: four batches of butter
            for (int i = 0; i < 4; i++) crock.insert(new ItemStack(Items.MILK_BUCKET));
            var recipe = crock.recipe(c.level()).orElseThrow().value();
            crock.seal(c.level(), recipe);
            crock.age(recipe.time());
            List<ItemStack> out = crock.open(c.level());
            check(count(out, item("butter")) == 12 && count(out, Items.BUCKET) == 4, "four buckets make 12 butter: %s", out);
        });
    }

    /** Breaking a crock before it's done gives the ingredients back, never the product early. */
    private static void crockBrokenEarly(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        with(c, pos, ModBlocks.FERMENTING_CROCK.get().defaultBlockState(), () -> {
            FermentingCrockBlockEntity crock = (FermentingCrockBlockEntity) c.level().getBlockEntity(pos);
            crock.insert(new ItemStack(Items.MILK_BUCKET));
            crock.seal(c.level(), crock.recipe(c.level()).orElseThrow().value());
            c.level().destroyBlock(pos, true);
            List<ItemStack> drops = dropsAround(c.level(), pos).stream().map(ItemEntity::getItem).toList();
            check(count(drops, Items.MILK_BUCKET) == 1 && count(drops, item("butter")) == 0, "broken early: the milk back, no butter: %s", drops);
        });
    }

    /**
     * The player's path, through the server's own right-click handling: milk goes in, an apple doesn't, sneaking with an
     * empty hand takes the milk back, an empty hand seals the lid, a sealed crock only reports, and once it's done an
     * empty hand lifts the lid for the butter.
     */
    private static void crockByHand(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        var inv = c.player().getInventory();
        with(c, pos, ModBlocks.FERMENTING_CROCK.get().defaultBlockState(), () -> {
            FermentingCrockBlockEntity crock = (FermentingCrockBlockEntity) c.level().getBlockEntity(pos);
            click(c, pos, new ItemStack(Items.MILK_BUCKET), false);
            check(c.player().getMainHandItem().isEmpty() && crock.contents().getFirst().is(Items.MILK_BUCKET), "the milk goes in: %s", crock.contents());
            click(c, pos, new ItemStack(Items.APPLE), false);
            check(c.player().getMainHandItem().is(Items.APPLE) && crock.contents().get(1).isEmpty(), "an apple doesn't ferment: it stays in the hand");
            int milkBefore = inv.countItem(Items.MILK_BUCKET);
            click(c, pos, ItemStack.EMPTY, true);
            check(inv.countItem(Items.MILK_BUCKET) - milkBefore == 1 && crock.contents().stream().allMatch(ItemStack::isEmpty),
                    "sneaking with an empty hand takes the milk back");
            take(inv, Items.MILK_BUCKET, 1);
            click(c, pos, new ItemStack(Items.MILK_BUCKET), false);
            click(c, pos, ItemStack.EMPTY, false);
            check(crock.sealed() && c.level().getBlockState(pos).getValue(FermentingCrockBlock.STAGE) == 1, "an empty hand seals the lid");
            click(c, pos, ItemStack.EMPTY, false);
            check(crock.sealed() && !crock.done(c.level()), "using a sealed crock early only says how far along it is");
            crock.age(FermentingRecipe.DEFAULT_TIME);
            int before = inv.countItem(item("butter"));
            click(c, pos, ItemStack.EMPTY, false);
            check(inv.countItem(item("butter")) - before == 3 && !crock.sealed(), "lifting the lid gives the butter");
            take(inv, item("butter"), 3);
            take(inv, Items.BUCKET, 1);
        });
    }

    /** Takes {@code count} of {@code item} back out of the inventory, so a check leaves it as it found it. */
    private static void take(net.minecraft.world.entity.player.Inventory inv, Item item, int count) {
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int n = Math.min(count, s.getCount());
            s.shrink(n);
            count -= n;
        }
    }

    /**
     * Sleeping skips the overworld clock ahead without ticking the world: the crock counts the clock too. A clock set
     * back (with /time) doesn't undo the ticks that passed.
     */
    private static void crockCountsANightSlept(RuleChecks.Ctx c) {
        crock(c, crock -> {
            crock.insert(new ItemStack(Items.MILK_BUCKET));
            crock.seal(c.level(), crock.recipe(c.level()).orElseThrow().value());
            crock.ageClock(FermentingRecipe.DEFAULT_TIME - 1);
            check(!crock.done(c.level()), "one tick short on the clock");
            crock.ageClock(1);
            check(crock.done(c.level()) && crock.stage(c.level()) == 3, "a night slept through finishes it");
        });
        crock(c, crock -> {
            crock.insert(new ItemStack(Items.MILK_BUCKET));
            crock.seal(c.level(), crock.recipe(c.level()).orElseThrow().value());
            crock.ageClock(-100_000);
            crock.age(FermentingRecipe.DEFAULT_TIME / 2);
            check(crock.stage(c.level()) == 2 && !crock.done(c.level()), "the clock set back: the ticks still count (stage %s)", crock.stage(c.level()));
        });
    }

    // ---- one full chain, through every station's real recipes ------------------------------------------------------

    /**
     * Wheat to a Gourmet meal (the reference chain, built before the other T4s): board cuts wheat to flour, crafting
     * makes dough, the crock ferments milk to butter, crafting makes pastry, the oven bakes apple pie, and eating it
     * starts a tier-4 meal with Swift, Fortune and Diligence III for 20 minutes.
     */
    private static void applePieChain(RuleChecks.Ctx c) {
        var level = c.level();
        var recipes = c.server().getRecipeManager();
        ItemStack flour = recipes.getRecipeFor(com.panda.fullcourse.registry.ModRecipes.CUTTING.get(),
                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.WHEAT)), level)
                .map(h -> h.value().assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.WHEAT)))).orElse(ItemStack.EMPTY);
        check(flour.is(item("flour")), "the board cuts wheat to flour: %s", flour);
        ItemStack dough = craft(c, flour, new ItemStack(Items.WATER_BUCKET));
        check(dough.is(item("dough")), "flour and water make dough: %s", dough);
        crock(c, crock -> {
            crock.insert(new ItemStack(Items.MILK_BUCKET));
            crock.seal(level, crock.recipe(level).orElseThrow().value());
            crock.age(FermentingRecipe.DEFAULT_TIME);
            List<ItemStack> out = crock.open(level);
            check(count(out, item("butter")) == 3, "the crock makes butter: %s", out);
        });
        ItemStack pastry = craft(c, dough, new ItemStack(item("butter")));
        check(pastry.is(item("pastry")), "dough and butter make pastry: %s", pastry);
        oven(c, List.of(new ItemStack(Items.APPLE), new ItemStack(Items.SUGAR), pastry), new ItemStack(Items.CHARCOAL), 300, oven ->
                check(oven.getItem(BrickOvenBlockEntity.OUTPUT).is(item("apple_pie")), "the oven bakes apple pie: %s",
                        oven.getItem(BrickOvenBlockEntity.OUTPUT)));
        com.panda.fullcourse.food.FoodRules.onEatDish(c.player(), item("apple_pie"), 9, c.t());
        var meal = com.panda.fullcourse.food.FoodRules.state(c.player()).meals().active().stream()
                .filter(m -> m.dish().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item("apple_pie")))).findFirst();
        check(meal.isPresent() && meal.get().tier().stars() == 4 && meal.get().level() == 3
                && meal.get().families().stream().map(f -> f.key()).toList().equals(List.of("swift", "fortune", "diligence"))
                && meal.get().end() - meal.get().start() == 20 * 60 * 20,
                "apple pie is a Gourmet meal: Swift, Fortune, Diligence III for 20:00: %s", meal);
    }

    private static ItemStack craft(RuleChecks.Ctx c, ItemStack a, ItemStack b) {
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(a.copyWithCount(1), b.copyWithCount(1)));
        return c.server().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, c.level())
                .map(h -> h.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    // ---- ember peppers ------------------------------------------------------------------------------------------

    private static void emberPepperNeedsSoulSoil(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        var peppers = ModBlocks.EMBER_PEPPERS.get().defaultBlockState();
        with(c, pos.below(), Blocks.SOUL_SOIL.defaultBlockState(), () -> {
            check(peppers.canSurvive(c.level(), pos), "peppers grow on soul soil");
            with(c, pos, peppers, () -> {
                for (int i = 0; i < 2000 && c.level().getBlockState(pos).getValue(NetherWartBlock.AGE) < 3; i++) {
                    c.level().getBlockState(pos).randomTick(c.level(), pos, c.level().getRandom());
                }
                check(c.level().getBlockState(pos).getValue(NetherWartBlock.AGE) == 3, "and ripen through four stages");
                var drops = net.minecraft.world.level.block.Block.getDrops(c.level().getBlockState(pos), c.level(), pos, null);
                check(count(drops, item("ember_pepper")) >= 2, "ripe peppers drop 2-4: %s", drops);
            });
        });
        with(c, pos.below(), Blocks.FARMLAND.defaultBlockState(), () -> check(!peppers.canSurvive(c.level(), pos), "not on farmland"));
        with(c, pos.below(), Blocks.SOUL_SAND.defaultBlockState(), () -> check(!peppers.canSurvive(c.level(), pos), "not on soul sand (that's wart)"));
    }

    private static void emberPatchPlaces(RuleChecks.Ctx c) {
        var feature = c.level().registryAccess().lookupOrThrow(Registries.FEATURE)
                .getOrThrow(ResourceKey.create(Registries.FEATURE, FullCourse.id("wild_ember_peppers")));
        BlockPos pos = c.spot();
        var generator = c.level().getChunkSource().getGenerator();
        with(c, pos.below(), Blocks.CRIMSON_NYLIUM.defaultBlockState(), () -> with(c, pos, Blocks.AIR.defaultBlockState(), () -> {
            feature.value().place(c.level(), generator, c.level().getRandom(), pos);
            check(c.level().getBlockState(pos).is(ModBlocks.WILD_EMBER_PEPPERS.get()), "wild peppers on crimson nylium: %s", c.level().getBlockState(pos));
        }));
        with(c, pos.below(), Blocks.NETHERRACK.defaultBlockState(), () -> with(c, pos, Blocks.AIR.defaultBlockState(), () -> {
            feature.value().place(c.level(), generator, c.level().getRandom(), pos);
            check(c.level().getBlockState(pos).isAir(), "none on netherrack: %s", c.level().getBlockState(pos));
        }));
    }

    private static void wildEmberPeppersInCrimsonForest(RuleChecks.Ctx c) {
        var placed = c.level().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, FullCourse.id("patch_wild_ember_peppers")));
        var biomes = c.level().registryAccess().lookupOrThrow(Registries.BIOME);
        check(biomes.getOrThrow(Biomes.CRIMSON_FOREST).value().getGenerationSettings().hasFeature(placed.value()), "crimson forests grow wild peppers");
        check(!biomes.getOrThrow(Biomes.WARPED_FOREST).value().getGenerationSettings().hasFeature(placed.value()), "warped forests don't");
    }
}
