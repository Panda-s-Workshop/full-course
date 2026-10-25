package com.panda.fullcourse.checks;

import static com.panda.fullcourse.checks.RuleChecks.check;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.panda.fullcourse.FullCourse;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStackTemplate;
import com.panda.fullcourse.compat.RecipeFields;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.food.Profile;
import com.panda.fullcourse.kitchen.CookingPotBlockEntity;
import com.panda.fullcourse.kitchen.CuttingBoardBlockEntity;
import com.panda.fullcourse.kitchen.KitchenTags;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.registry.ModBlocks;
import com.panda.fullcourse.registry.ModItems;
import com.panda.fullcourse.registry.ModRecipes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The kitchen checked on a real server: the board, the pot, its heat and hopper faces, the onion, and every
 * dish's tier against the design. Part of {@link RuleChecks#CHECKS}, so both loaders run them.
 */
final class KitchenChecks {
    private KitchenChecks() {}

    /** Shaped like Create's processing output: a result made through {@code create()}. */
    public record Output(Item item, int count) {
        public ItemStack create() {
            return new ItemStack(item, count);
        }
    }

    /** Shaped like Create's sized ingredient: a class handing its ingredient over. */
    public static final class Sized {
        private final Ingredient ingredient;

        Sized(Ingredient ingredient) {
            this.ingredient = ingredient;
        }

        public Ingredient getIngredient() {
            return ingredient;
        }
    }

    /** Shaped like Create's mixing recipe: outputs first, fluids, then sized ingredients. */
    public record Mixing(int time, List<Output> results, List<String> fluidIngredients, List<Sized> ingredients) {}

    /** Shaped like Create's compacting of a fluid alone: no item goes in. */
    public record Compacting(List<String> fluidIngredients, List<Output> results) {}

    /** A machine record whose input isn't an ingredient (a block): its result is known, what it's made from isn't. */
    public record Planting(String block, List<Output> results) {}

    /** Shaped like Create's sequenced assembly: a transitional item before the results. */
    public record Assembly(Ingredient ingredient, ItemStackTemplate transitionalItem, List<Output> results) {}

    static void register(Map<String, Consumer<RuleChecks.Ctx>> checks) {
        checks.put("recipes_loaded", KitchenChecks::recipesLoaded);
        checks.put("recipes_shadow_nothing", KitchenChecks::recipesShadowNothing);
        checks.put("machine_recipe_fields", KitchenChecks::machineRecipeFields);
        checks.put("roster_tiers", KitchenChecks::rosterTiers);
        checks.put("board_cuts_with_knife", KitchenChecks::boardCutsWithKnife);
        checks.put("board_needs_recipe", KitchenChecks::boardNeedsRecipe);
        checks.put("pot_needs_heat", KitchenChecks::potNeedsHeat);
        checks.put("pot_cooks_stew", KitchenChecks::potCooksStew);
        checks.put("pot_returns_containers", KitchenChecks::potReturnsContainers);
        checks.put("pot_keeps_soup_color", KitchenChecks::potKeepsSoupColor);
        checks.put("pot_stray_item_blocks", KitchenChecks::potStrayItemBlocks);
        checks.put("pot_hopper_faces", KitchenChecks::potHopperFaces);
        checks.put("pot_pools_split_stacks", KitchenChecks::potPoolsSplitStacks);
        checks.put("pot_keeps_empties", KitchenChecks::potKeepsEmpties);
        checks.put("pot_heat_through_hopper", KitchenChecks::potHeatThroughHopper);
        checks.put("pot_says_why", KitchenChecks::potSaysWhy);
        checks.put("night_eye_gives_night_vision", KitchenChecks::nightEyeGivesNightVision);
        checks.put("onion_plants_crop", KitchenChecks::onionPlantsCrop);
        checks.put("onion_grows", KitchenChecks::onionGrows);
        checks.put("onion_patch_places", KitchenChecks::onionPatchPlaces);
        checks.put("wild_onions_drop_onions", KitchenChecks::wildOnionsDropOnions);
        checks.put("wild_onions_in_plains", KitchenChecks::wildOnionsInPlains);
        checks.put("meal_advancements", KitchenChecks::mealAdvancements);
        checks.put("tier_command", KitchenChecks::tierCommand);
        checks.put("meals_command", KitchenChecks::mealsCommand);
        checks.put("unlock_command", KitchenChecks::unlockCommand);
        StationChecks.register(checks);
        FeastChecks.register(checks);
    }

    private static Item item(String path) {
        return ModItems.item(path);
    }

    // ---- data ---------------------------------------------------------------------------------------------------

    /** Every table item but the crops has a recipe that makes it; each station has its recipe type. */
    /** Machine records are read from their fields, the way Create keeps them. */
    private static void machineRecipeFields(RuleChecks.Ctx c) {
        var mixing = RecipeFields.fields(new Mixing(100, List.of(new Output(Items.BREAD, 2)), List.of("milk"),
                List.of(new Sized(Ingredient.of(Items.WHEAT)), new Sized(Ingredient.of(Items.SUGAR)))));
        check(mixing.isPresent() && mixing.get().ingredients().size() == 2 && mixing.get().result().is(Items.BREAD)
                && mixing.get().result().getCount() == 2, "a mixing record reads 2 sized ingredients and its output: %s", mixing);
        var assembly = RecipeFields.fields(new Assembly(Ingredient.of(Items.WHEAT), new ItemStackTemplate(Items.STICK),
                List.of(new Output(Items.CAKE, 1))));
        check(assembly.isPresent() && assembly.get().result().is(Items.CAKE),
                "an assembly's result is its results, not its transitional item: %s", assembly);
        var compacting = RecipeFields.fields(new Compacting(List.of("chocolate"), List.of(new Output(Items.COOKIE, 1))));
        check(compacting.isPresent() && compacting.get().ingredients().isEmpty() && compacting.get().fluids() && !compacting.get().unread(),
                "a recipe of fluids alone is read, with no ingredients: %s", compacting);
        var planting = RecipeFields.fields(new Planting("minecraft:farmland", List.of(new Output(Items.CARROT, 1))));
        check(planting.isPresent() && planting.get().unread() && planting.get().result().is(Items.CARROT),
                "a record of no ingredient and no fluid is unread, its result known: %s", planting);
    }

    /**
     * No furnace, smoker or campfire recipe of ours takes an input another recipe of the same kind takes: the station
     * picks one, and ours sorting first would take vanilla's away (chorus fruit pops for purpur). Likewise no crafting
     * grid of ours (each ingredient's first item, in its shape) is one another crafting recipe takes too, as our flint
     * knife and Farmer's Delight's would be if ours didn't step aside.
     */
    private static void recipesShadowNothing(RuleChecks.Ctx c) {
        var recipes = c.server().getRecipeManager().getRecipes();
        List<String> clashes = new ArrayList<>();
        for (RecipeHolder<?> ours : recipes) {
            if (!ours.id().identifier().getNamespace().equals(FullCourse.MODID)
                    || !(ours.value() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cooking)) continue;
            cooking.input().items().forEach(item -> {
                ItemStack stack = new ItemStack(item);
                for (RecipeHolder<?> other : recipes) {
                    if (other != ours && other.value().getType() == cooking.getType()
                            && other.value() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe o && o.input().test(stack)) {
                        clashes.add(ours.id().identifier() + " and " + other.id().identifier() + " both take " + item.getRegisteredName());
                    }
                }
            });
        }
        for (RecipeHolder<?> ours : recipes) {
            if (!ours.id().identifier().getNamespace().equals(FullCourse.MODID)
                    || !(ours.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting)) continue;
            var grid = sampleGrid(crafting);
            if (grid == null) continue;
            // the sample is a grid the recipe itself takes, or the search below finds nothing for nothing
            if (!crafting.matches(grid, c.level())) clashes.add(ours.id().identifier() + " doesn't take its own sample grid");
            for (RecipeHolder<?> other : recipes) {
                if (other != ours && other.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe o && o.matches(grid, c.level())) {
                    clashes.add(ours.id().identifier() + " and " + other.id().identifier() + " both take the same grid");
                }
            }
        }
        check(clashes.isEmpty(), "recipes of one kind share an input: %s", clashes);
        var input = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.CHORUS_FRUIT));
        ItemStack popped = c.server().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, c.level())
                .map(h -> h.value().assemble(input)).orElse(ItemStack.EMPTY);
        check(popped.is(Items.POPPED_CHORUS_FRUIT), "a furnace should pop chorus fruit for purpur: %s", popped);
    }

    /** A grid a crafting recipe takes: each ingredient's first item, in the recipe's shape; null for a special recipe. */
    private static net.minecraft.world.item.crafting.CraftingInput sampleGrid(net.minecraft.world.item.crafting.CraftingRecipe recipe) {
        List<ItemStack> items = new ArrayList<>();
        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            for (var slot : shaped.getIngredients()) items.add(slot.flatMap(i -> i.items().findFirst()).map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY));
            return net.minecraft.world.item.crafting.CraftingInput.of(shaped.getWidth(), shaped.getHeight(), items);
        }
        if (recipe.placementInfo().isImpossibleToPlace()) return null;
        for (var ingredient : recipe.placementInfo().ingredients()) items.add(ingredient.items().findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY));
        if (items.isEmpty() || items.size() > 9) return null;
        while (items.size() < 9) items.add(ItemStack.EMPTY);
        return net.minecraft.world.item.crafting.CraftingInput.of(3, 3, items);
    }

        private static void recipesLoaded(RuleChecks.Ctx c) {
        var context = SlotDisplayContext.fromLevel(c.level());
        Set<Item> made = new HashSet<>();
        int cutting = 0, cooking = 0, baking = 0, fermenting = 0;
        for (RecipeHolder<?> holder : c.server().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.getType() == ModRecipes.CUTTING.get()) cutting++;
            if (recipe.getType() == ModRecipes.COOKING.get()) cooking++;
            if (recipe.getType() == ModRecipes.BAKING.get()) baking++;
            if (recipe.getType() == ModRecipes.FERMENTING.get()) fermenting++;
            for (RecipeDisplay display : recipe.display()) made.add(display.result().resolveForFirstStack(context).getItem());
        }
        check(cutting >= 5 && cooking >= 10 && baking >= 7 && fermenting >= 4, "cutting %s, cooking %s, baking %s, fermenting %s recipes",
                cutting, cooking, baking, fermenting);
        List<String> missing = new ArrayList<>();
        for (String path : ModItems.TABLE.keySet()) {
            if (!Set.of("onion", "ember_pepper").contains(path) && !made.contains(item(path))) missing.add(path); // crops grow
        }
        check(missing.isEmpty(), "no recipe makes %s", missing);
        check(made.contains(ModItems.CUTTING_BOARD.get()) && made.contains(ModItems.COOKING_POT.get()) && made.contains(ModItems.BRICK_OVEN.get())
                && made.contains(ModItems.FERMENTING_CROCK.get()), "the kitchen blocks are craftable");
        // our soups and pies are in the shared tags other mods' recipes take
        var soup = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "foods/soup"));
        var pie = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "foods/pie"));
        check(item("fish_chowder").builtInRegistryHolder().is(soup) && item("apple_pie").builtInRegistryHolder().is(pie),
                "fish chowder is a c:foods/soup and apple pie a c:foods/pie");
    }

    /** Every dish's tier, scored on the real recipes, is its designed tier; its families are the design's. */
    private static void rosterTiers(RuleChecks.Ctx c) {
        List<String> wrong = new ArrayList<>();
        for (JsonElement el : ModItems.table()) {
            JsonObject e = el.getAsJsonObject();
            if (!e.has("tier")) continue; // dishes, and the intermediates you can eat
            String path = e.get("id").getAsString();
            Profile p = FoodProfiles.get(c.server(), item(path));
            int tier = e.get("tier").getAsInt();
            List<String> families = new ArrayList<>();
            e.getAsJsonArray("families").forEach(f -> families.add(f.getAsString()));
            List<String> got = p.families().stream().map(Family::key).toList();
            if (p.tier().stars() != tier || !got.equals(families)) {
                wrong.add(path + " T" + p.tier().stars() + " " + got + " (score " + p.score() + "), designed T" + tier + " " + families);
            }
        }
        check(wrong.isEmpty(), "foods off their design: %s", wrong);
    }

    // ---- the Cutting Board ---------------------------------------------------------------------------------------

    static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    static InteractionResult use(RuleChecks.Ctx c, BlockPos pos, ItemStack held) {
        c.player().setItemInHand(InteractionHand.MAIN_HAND, held);
        return c.level().getBlockState(pos).useItemOn(held, c.level(), c.player(), InteractionHand.MAIN_HAND, hit(pos));
    }

    /**
     * A right-click as the server takes it from a survival player: the block sees the held item first and, if it passes,
     * the empty-hand use. {@code held} is put in the main hand; read the hand afterwards for what's left.
     */
    static InteractionResult click(RuleChecks.Ctx c, BlockPos pos, ItemStack held, boolean sneaking) {
        ServerPlayer p = c.player();
        GameType mode = p.gameMode();
        p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneaking);
        try {
            return p.gameMode.useItemOn(p, c.level(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit(pos));
        } finally {
            p.setShiftKeyDown(false);
            p.setGameMode(mode);
        }
    }

    static List<ItemEntity> dropsAround(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
    }

    /** Places a block for the length of a check and puts back what was there. */
    static void with(RuleChecks.Ctx c, BlockPos pos, BlockState state, Runnable body) {
        BlockState before = c.level().getBlockState(pos);
        c.level().setBlock(pos, state, Block.UPDATE_ALL);
        try {
            body.run();
        } finally {
            c.level().setBlock(pos, before, Block.UPDATE_ALL);
            dropsAround(c.level(), pos).forEach(ItemEntity::discard);
            c.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static void boardCutsWithKnife(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        with(c, pos, ModBlocks.CUTTING_BOARD.get().defaultBlockState(), () -> {
            ItemStack wheat = new ItemStack(Items.WHEAT, 2);
            use(c, pos, wheat);
            CuttingBoardBlockEntity board = (CuttingBoardBlockEntity) c.level().getBlockEntity(pos);
            check(board.item().is(Items.WHEAT) && board.item().getCount() == 2 && wheat.isEmpty(),
                    "the stack lies on the board: board %s, hand %s", board.item(), wheat);
            // more wheat adds to the stack, up to a full one, and leaves the rest in hand
            ItemStack more = new ItemStack(Items.WHEAT, 63);
            use(c, pos, more);
            check(board.item().getCount() == 64 && more.getCount() == 1, "more wheat adds to the stack: board %s, hand %s", board.item(), more);
            board.setItem(new ItemStack(Items.WHEAT, 2));
            ItemStack knife = new ItemStack(item("iron_knife"));
            check(knife.is(KitchenTags.KNIVES), "the iron knife is a knife");
            c.player().getAdvancements().revoke(c.server().getAdvancements().get(FullCourse.id("cutting_board")), "done");
            use(c, pos, knife);
            check(done(c, "cutting_board"), "the first cut grants Mise en Place");
            check(board.item().getCount() == 1, "one cut takes one wheat: %s", board.item());
            check(knife.getDamageValue() == 1, "one cut wears the knife: damage %s", knife.getDamageValue());
            List<ItemStack> out = dropsAround(c.level(), pos).stream().map(ItemEntity::getItem).toList();
            check(out.size() == 1 && out.getFirst().is(item("flour")), "the cut pops out flour: %s", out);
            use(c, pos, ItemStack.EMPTY);
            c.level().getBlockState(pos).useWithoutItem(c.level(), c.player(), hit(pos));
            check(board.item().isEmpty(), "an empty hand takes the rest back: %s", board.item());
        });
    }

    private static void boardNeedsRecipe(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        with(c, pos, ModBlocks.CUTTING_BOARD.get().defaultBlockState(), () -> {
            use(c, pos, new ItemStack(Items.DIRT));
            ItemStack knife = new ItemStack(item("flint_knife"));
            use(c, pos, knife);
            CuttingBoardBlockEntity board = (CuttingBoardBlockEntity) c.level().getBlockEntity(pos);
            check(board.item().is(Items.DIRT) && knife.getDamageValue() == 0 && dropsAround(c.level(), pos).isEmpty(),
                    "dirt can't be cut: board %s, knife damage %s", board.item(), knife.getDamageValue());
        });
    }

    // ---- the Cooking Pot ----------------------------------------------------------------------------------------

    /** A pot at the spot over {@code below}; fills it, ticks it {@code ticks} times, hands it to {@code after}. */
    private static void pot(RuleChecks.Ctx c, BlockState below, List<ItemStack> ingredients, ItemStack container, int ticks,
            Consumer<CookingPotBlockEntity> after) {
        BlockPos pos = c.spot();
        with(c, pos.below(), below, () -> with(c, pos, ModBlocks.COOKING_POT.get().defaultBlockState(), () -> {
            CookingPotBlockEntity pot = (CookingPotBlockEntity) c.level().getBlockEntity(pos);
            for (int i = 0; i < ingredients.size(); i++) pot.setItem(i, ingredients.get(i));
            pot.setItem(CookingPotBlockEntity.CONTAINER, container);
            for (int i = 0; i < ticks; i++) CookingPotBlockEntity.serverTick(c.level(), pos, c.level().getBlockState(pos), pot);
            after.accept(pot);
        }));
    }

    private static ItemStack out(CookingPotBlockEntity pot) {
        return pot.getItem(CookingPotBlockEntity.OUTPUT);
    }

    private static void potNeedsHeat(RuleChecks.Ctx c) {
        List<ItemStack> bone = List.of(new ItemStack(Items.BONE));
        pot(c, Blocks.STONE.defaultBlockState(), bone, new ItemStack(Items.GLASS_BOTTLE), 400, pot ->
                check(out(pot).isEmpty() && pot.progress() == 0 && pot.getItem(0).is(Items.BONE), "no heat, no broth: out %s", out(pot)));
        pot(c, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false),
                List.of(new ItemStack(Items.BONE)), new ItemStack(Items.GLASS_BOTTLE), 400, pot ->
                        check(out(pot).isEmpty(), "an unlit campfire doesn't heat: out %s", out(pot)));
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(new ItemStack(Items.BONE)), new ItemStack(Items.GLASS_BOTTLE), 199, pot ->
                check(out(pot).isEmpty() && pot.progress() == 199, "still cooking at 199 ticks: progress %s", pot.progress()));
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(new ItemStack(Items.BONE)), new ItemStack(Items.GLASS_BOTTLE), 200, pot -> {
            check(out(pot).is(item("broth")), "a lit campfire cooks broth in 10 s: out %s", out(pot));
            check(pot.getItem(0).isEmpty() && pot.getItem(CookingPotBlockEntity.CONTAINER).isEmpty(), "the bone and the bottle are used up");
        });
    }

    private static void potCooksStew(RuleChecks.Ctx c) {
        List<ItemStack> stew = List.of(new ItemStack(item("chopped_vegetables")), new ItemStack(item("chopped_onion")),
                new ItemStack(Items.BAKED_POTATO, 3), new ItemStack(item("broth")));
        pot(c, Blocks.MAGMA_BLOCK.defaultBlockState(), stew, new ItemStack(Items.BOWL, 2), 200, pot -> {
            check(out(pot).is(item("vegetable_stew")), "vegetable stew: out %s", out(pot));
            check(pot.getItem(2).getCount() == 2 && pot.getItem(CookingPotBlockEntity.CONTAINER).getCount() == 1,
                    "one potato and one bowl used: %s, %s", pot.getItem(2), pot.getItem(CookingPotBlockEntity.CONTAINER));
            check(pot.getItem(3).is(Items.GLASS_BOTTLE), "the broth leaves its bottle in its slot: %s", pot.getItem(3));
        });
    }

    /** The pot's soup takes its recipe's colour, and keeps it through a save and a load (a restart, a chunk reload). */
    private static void potKeepsSoupColor(RuleChecks.Ctx c) {
        List<ItemStack> stew = List.of(new ItemStack(item("chopped_vegetables")), new ItemStack(item("chopped_onion")),
                new ItemStack(Items.BAKED_POTATO, 3), new ItemStack(item("broth")));
        pot(c, Blocks.MAGMA_BLOCK.defaultBlockState(), stew, new ItemStack(Items.BOWL, 2), 200, pot -> {
            int color = 0xB87A3A; // vegetable_stew's soup_color
            check(pot.soupColor() == color, "the stew's colour: %06X, expected %06X", pot.soupColor(), color);
            var registries = c.level().registryAccess();
            var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pot.getBlockPos(), pot.getBlockState(),
                    pot.saveWithFullMetadata(registries), registries);
            check(loaded instanceof CookingPotBlockEntity again && again.soupColor() == color, "after a load: %s",
                    loaded instanceof CookingPotBlockEntity again ? String.format("%06X", again.soupColor()) : loaded);
        });
    }

    private static void potReturnsContainers(RuleChecks.Ctx c) {
        List<ItemStack> chowder = List.of(new ItemStack(item("fish_fillet")), new ItemStack(Items.BAKED_POTATO),
                new ItemStack(Items.MILK_BUCKET), new ItemStack(item("broth")));
        pot(c, Blocks.LAVA.defaultBlockState(), chowder, new ItemStack(Items.BOWL), 200, pot -> {
            check(out(pot).is(item("fish_chowder")), "fish chowder over lava: out %s", out(pot));
            check(pot.getItem(2).is(Items.BUCKET) && pot.getItem(3).is(Items.GLASS_BOTTLE), "milk leaves a bucket, broth a bottle: %s, %s",
                    pot.getItem(2), pot.getItem(3));
        });
    }

    private static void potStrayItemBlocks(RuleChecks.Ctx c) {
        List<ItemStack> withDirt = List.of(new ItemStack(Items.BONE), new ItemStack(Items.DIRT));
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), withDirt, new ItemStack(Items.GLASS_BOTTLE), 200, pot ->
                check(out(pot).isEmpty() && pot.progress() == 0, "a stray item stops the recipe: out %s", out(pot)));
    }

    /** What a hopper would do (vanilla's own transfer, HopperBlockEntity.addItem): top to ingredients, sides to the container slot, out from below. */
    private static void potHopperFaces(RuleChecks.Ctx c) {
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(), ItemStack.EMPTY, 0, pot -> {
            ItemStack left = HopperBlockEntity.addItem(null, pot, new ItemStack(Items.BONE), Direction.UP);
            check(left.isEmpty() && pot.getItem(0).is(Items.BONE), "from above: into the ingredients: %s", pot.getItem(0));
            left = HopperBlockEntity.addItem(null, pot, new ItemStack(Items.GLASS_BOTTLE), Direction.NORTH);
            check(left.isEmpty() && pot.getItem(CookingPotBlockEntity.CONTAINER).is(Items.GLASS_BOTTLE), "from the side: into the container slot");
            pot.setItem(CookingPotBlockEntity.OUTPUT, new ItemStack(item("broth")));
            check(pot.canTakeItemThroughFace(CookingPotBlockEntity.OUTPUT, out(pot), Direction.DOWN)
                    && !pot.canTakeItemThroughFace(0, pot.getItem(0), Direction.DOWN)
                    && !pot.canTakeItemThroughFace(CookingPotBlockEntity.OUTPUT, out(pot), Direction.NORTH), "only the dish (and empties) out of the bottom");
            check(!pot.canPlaceItemThroughFace(CookingPotBlockEntity.OUTPUT, new ItemStack(Items.BONE), Direction.UP), "nothing goes into the output");
        });
    }

    /** A hopper splits a stack over two slots: they're one pool, not a stray. */
    private static void potPoolsSplitStacks(RuleChecks.Ctx c) {
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(new ItemStack(Items.BONE), new ItemStack(Items.BONE)),
                new ItemStack(Items.GLASS_BOTTLE, 2), 200, pot -> {
                    check(out(pot).is(item("broth")), "two slots of bone still make broth: out %s", out(pot));
                    check(pot.getItem(0).getCount() + pot.getItem(1).getCount() == 1, "one bone used");
                });
        // an empty an earlier dish left in a slot doesn't block the next one
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.BONE)),
                new ItemStack(Items.GLASS_BOTTLE), 200, pot ->
                        check(out(pot).is(item("broth")), "a bottle in an ingredient slot is spare, not a stray: out %s", out(pot)));
    }

    /** The empty from a stack that isn't used up stays in the pot. */
    private static void potKeepsEmpties(RuleChecks.Ctx c) {
        List<ItemStack> stew = List.of(new ItemStack(item("chopped_vegetables")), new ItemStack(item("chopped_onion")),
                new ItemStack(Items.BAKED_POTATO), new ItemStack(item("broth"), 3));
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), stew, new ItemStack(Items.BOWL, 2), 200, pot -> {
            check(out(pot).is(item("vegetable_stew")), "stew: out %s", out(pot));
            int bottles = 0;
            for (int i = 0; i < CookingPotBlockEntity.INGREDIENTS; i++) if (pot.getItem(i).is(Items.GLASS_BOTTLE)) bottles += pot.getItem(i).getCount();
            long dropped = dropsAround(c.level(), c.spot()).stream().filter(e -> e.getItem().is(Items.GLASS_BOTTLE)).count();
            check(bottles == 1 && dropped == 0, "the broth's bottle stays in the pot: %s in the pot, %s dropped", bottles, dropped);
            check(!pot.canPlaceItem(CookingPotBlockEntity.CONTAINER, new ItemStack(Items.BONE))
                    && pot.canPlaceItem(CookingPotBlockEntity.CONTAINER, new ItemStack(Items.BOWL)), "the container slot takes containers only");
        });
    }

    /** Heat rises through a hopper, so a heated pot can be emptied from below. */
    private static void potHeatThroughHopper(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        with(c, pos.below(2), Blocks.CAMPFIRE.defaultBlockState(), () -> with(c, pos.below(), Blocks.HOPPER.defaultBlockState(), () -> {
            check(CookingPotBlockEntity.heated(c.level(), pos), "a campfire under a hopper heats the pot on it");
            with(c, pos.below(2), Blocks.STONE.defaultBlockState(), () ->
                    check(!CookingPotBlockEntity.heated(c.level(), pos), "stone under the hopper doesn't"));
        }));
    }

    /** The screen's status line: why the pot isn't cooking. */
    private static void potSaysWhy(RuleChecks.Ctx c) {
        // fresh stacks each time: breaking the pot after a case drops (and so empties) the stacks it held
        pot(c, Blocks.STONE.defaultBlockState(), List.of(new ItemStack(Items.BONE)), new ItemStack(Items.GLASS_BOTTLE), 1, pot ->
                check(pot.status() == CookingPotBlockEntity.Status.NO_HEAT, "no heat: %s", pot.status()));
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(new ItemStack(Items.BONE)), ItemStack.EMPTY, 1, pot ->
                check(pot.status() == CookingPotBlockEntity.Status.NEEDS_CONTAINER, "no bottle: %s", pot.status()));
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(new ItemStack(Items.BONE), new ItemStack(Items.DIRT)), new ItemStack(Items.GLASS_BOTTLE), 1, pot ->
                check(pot.status() == CookingPotBlockEntity.Status.NO_RECIPE, "a stray: %s", pot.status()));
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(), ItemStack.EMPTY, 1, pot ->
                check(pot.status() == CookingPotBlockEntity.Status.EMPTY, "empty: %s", pot.status()));
        pot(c, Blocks.CAMPFIRE.defaultBlockState(), List.of(new ItemStack(Items.BONE)), new ItemStack(Items.GLASS_BOTTLE), 1, pot ->
                check(pot.status() == CookingPotBlockEntity.Status.COOKING, "cooking: %s", pot.status()));
        BlockPos pos = c.spot();
        with(c, pos.below(), Blocks.CAMPFIRE.defaultBlockState(), () -> with(c, pos, ModBlocks.COOKING_POT.get().defaultBlockState(), () -> {
            CookingPotBlockEntity pot = (CookingPotBlockEntity) c.level().getBlockEntity(pos);
            pot.setItem(0, new ItemStack(Items.BONE));
            pot.setItem(CookingPotBlockEntity.CONTAINER, new ItemStack(Items.GLASS_BOTTLE));
            ItemStack full = new ItemStack(item("broth"));
            full.setCount(full.getMaxStackSize());
            pot.setItem(CookingPotBlockEntity.OUTPUT, full);
            CookingPotBlockEntity.serverTick(c.level(), pos, c.level().getBlockState(pos), pot);
            check(pot.status() == CookingPotBlockEntity.Status.OUTPUT_FULL, "a full output: %s", pot.status());
        }));
    }

    /** Night Eye is the one family that isn't an attribute: it keeps Night Vision up, without an icon. */
    private static void nightEyeGivesNightVision(RuleChecks.Ctx c) {
        c.player().removeEffect(MobEffects.NIGHT_VISION);
        FoodRules.onEatDish(c.player(), item("mushroom_skewer"), 3, c.t());
        FoodRules.tick(c.player(), c.t() + 1);
        MobEffectInstance vision = c.player().getEffect(MobEffects.NIGHT_VISION);
        check(vision != null && vision.isAmbient() && !vision.showIcon(), "a mushroom skewer gives hidden Night Vision: %s", vision);
        c.player().removeEffect(MobEffects.NIGHT_VISION);
        // under /tick freeze the player's effects still count down: the meal keeps night vision on
        var clock = c.server().tickRateManager();
        clock.setFrozen(true);
        try {
            clock.tick();
            c.player().tickCount = 40;
            FoodRules.tick(c.player(), c.t() + 2);
            check(c.player().hasEffect(MobEffects.NIGHT_VISION), "no night vision under /tick freeze");
        } finally {
            clock.setFrozen(false);
            clock.tick();
            c.player().removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    // ---- onions -------------------------------------------------------------------------------------------------

    private static void onionPlantsCrop(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        with(c, pos.below(), Blocks.FARMLAND.defaultBlockState(), () -> with(c, pos, Blocks.AIR.defaultBlockState(), () -> {
            ItemStack onion = new ItemStack(item("onion"), 2);
            c.player().setItemInHand(InteractionHand.MAIN_HAND, onion);
            onion.useOn(new UseOnContext(c.player(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false)));
            check(c.level().getBlockState(pos).is(ModBlocks.ONIONS.get()) && onion.getCount() == 1, "an onion plants onions on farmland: %s",
                    c.level().getBlockState(pos));
        }));
    }

    /** Planted onions grow through their stages to ripe (bone meal, the crop's own growth step). */
    private static void onionGrows(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        with(c, pos.below(), Blocks.FARMLAND.defaultBlockState(), () -> with(c, pos, ModBlocks.ONIONS.get().defaultBlockState(), () -> {
            List<Integer> ages = new ArrayList<>();
            for (int i = 0; i < 20 && ModBlocks.ONIONS.get().getAge(c.level().getBlockState(pos)) < 7; i++) {
                BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), c.level(), pos);
                ages.add(ModBlocks.ONIONS.get().getAge(c.level().getBlockState(pos)));
            }
            check(!ages.isEmpty() && ages.getLast() == 7, "onions grow to age 7: %s", ages);
            for (int i = 1; i < ages.size(); i++) check(ages.get(i) >= ages.get(i - 1), "growth only goes up: %s", ages);
        }));
    }

    /** The patch's feature puts wild onions on grass, and not on stone (its placement filter asks for grass too). */
    private static void onionPatchPlaces(RuleChecks.Ctx c) {
        var feature = c.level().registryAccess().lookupOrThrow(Registries.FEATURE)
                .getOrThrow(ResourceKey.create(Registries.FEATURE, FullCourse.id("wild_onions")));
        BlockPos pos = c.spot();
        var generator = c.level().getChunkSource().getGenerator();
        with(c, pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), () -> with(c, pos, Blocks.AIR.defaultBlockState(), () -> {
            feature.value().place(c.level(), generator, c.level().getRandom(), pos);
            check(c.level().getBlockState(pos).is(ModBlocks.WILD_ONIONS.get()), "wild onions on grass: %s", c.level().getBlockState(pos));
        }));
        with(c, pos.below(), Blocks.STONE.defaultBlockState(), () -> with(c, pos, Blocks.AIR.defaultBlockState(), () -> {
            feature.value().place(c.level(), generator, c.level().getRandom(), pos);
            check(c.level().getBlockState(pos).isAir(), "none on stone: %s", c.level().getBlockState(pos));
        }));
    }

    private static void wildOnionsDropOnions(RuleChecks.Ctx c) {
        BlockPos pos = c.spot();
        List<ItemStack> drops = Block.getDrops(ModBlocks.WILD_ONIONS.get().defaultBlockState(), c.level(), pos, null);
        check(drops.size() == 1 && drops.getFirst().is(item("onion")) && drops.getFirst().getCount() >= 1 && drops.getFirst().getCount() <= 2,
                "wild onions drop 1-2 onions: %s", drops);
        var ripe = ModBlocks.ONIONS.get().getStateForAge(7);
        int onions = Block.getDrops(ripe, c.level(), pos, null).stream().filter(s -> s.is(item("onion"))).mapToInt(ItemStack::getCount).sum();
        check(onions >= 1, "ripe onions drop onions: %s", onions);
    }

    /** The patch is in a plains biome's features: NeoForge's biome modifier or Fabric's BiomeModifications took. */
    private static void wildOnionsInPlains(RuleChecks.Ctx c) {
        var placed = c.level().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, FullCourse.id("patch_wild_onions")));
        var plains = c.level().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var desert = c.level().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT);
        check(plains.value().getGenerationSettings().hasFeature(placed.value()), "plains should grow wild onions");
        // a biome named only by the shared c:is_taiga tag, as another mod's taiga would be
        var pine = c.level().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.OLD_GROWTH_PINE_TAIGA);
        check(pine.value().getGenerationSettings().hasFeature(placed.value()), "a c:is_taiga biome should grow wild onions");
        check(!desert.value().getGenerationSettings().hasFeature(placed.value()), "deserts shouldn't");
    }

    // ---- advancements and the command ---------------------------------------------------------------------------

    static boolean done(RuleChecks.Ctx c, String key) {
        var holder = c.server().getAdvancements().get(FullCourse.id(key));
        check(holder != null, "advancement %s is missing", key);
        return c.player().getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static void mealAdvancements(RuleChecks.Ctx c) {
        for (String key : List.of("first_meal", "hearty")) {
            c.player().getAdvancements().revoke(c.server().getAdvancements().get(FullCourse.id(key)), "done");
        }
        FoodRules.onEatDish(c.player(), Items.APPLE, 4, c.t());
        check(!done(c, "first_meal"), "a raw apple gives no meal, so no advancement");
        FoodRules.onEatDish(c.player(), item("fried_egg"), 4, c.t());
        check(done(c, "first_meal") && !done(c, "hearty"), "a fried egg (one star) is a first meal, not a hearty one");
        FoodRules.onEatDish(c.player(), item("vegetable_stew"), 8, c.t());
        check(done(c, "hearty"), "vegetable stew (three stars) is hearty");
    }

    private static void tierCommand(RuleChecks.Ctx c) {
        var source = c.player().createCommandSourceStack().withSuppressedOutput();
        var dispatcher = c.server().getCommands().getDispatcher();
        try {
            int stew = dispatcher.execute("fullcourse tier fullcourse:vegetable_stew", source);
            int dirt = dispatcher.execute("fullcourse tier minecraft:dirt", source);
            check(stew == 3 && dirt == 0, "/fullcourse tier: stew %s (3 stars), dirt %s (not a food)", stew, dirt);
        } catch (CommandSyntaxException e) {
            check(false, "/fullcourse tier failed to parse: %s", e.getMessage());
        }
    }

    /** {@code /fullcourse meals} counts the player's meals; naming a player needs operator rights. */
    private static void mealsCommand(RuleChecks.Ctx c) {
        Services.PLATFORM.setFoodState(c.player(), FoodState.EMPTY);
        FoodRules.onEatDish(c.player(), item("vegetable_stew"), 6);
        var dispatcher = c.server().getCommands().getDispatcher();
        var player = c.player().createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS).withSuppressedOutput();
        try {
            int own = dispatcher.execute("fullcourse meals", player);
            int named = dispatcher.execute("fullcourse meals @s", operator(c));
            check(own == 1 && named == 1, "/fullcourse meals: %s and %s by name (1 meal)", own, named);
        } catch (CommandSyntaxException e) {
            check(false, "/fullcourse meals failed to parse: %s", e.getMessage());
        }
        check(!parses(c, "fullcourse meals @s", player), "naming a player in /fullcourse meals needs operator rights");
    }

    private static CommandSourceStack operator(RuleChecks.Ctx c) {
        return c.server().createCommandSourceStack().withEntity(c.player()).withSuppressedOutput();
    }

    private static boolean parses(RuleChecks.Ctx c, String command, CommandSourceStack source) {
        return !c.server().getCommands().getDispatcher().parse(command, source).getReader().canRead();
    }

    /** {@code /fullcourse unlock} raises the slots, never lowers them, and stops at the config's maximum. */
    private static void unlockCommand(RuleChecks.Ctx c) {
        Services.PLATFORM.setFoodState(c.player(), FoodState.EMPTY);
        var dispatcher = c.server().getCommands().getDispatcher();
        var source = operator(c);
        String who = "@s";
        try {
            int up = dispatcher.execute("fullcourse unlock " + who + " 4", source);
            int four = FoodRules.state(c.player()).slots();
            int down = dispatcher.execute("fullcourse unlock " + who + " 3", source);
            int still = FoodRules.state(c.player()).slots();
            int over = dispatcher.execute("fullcourse unlock " + who + " " + (FullCourseConfig.maxMealSlots() + 1), source);
            check(up == 1 && four == 4 && down == 0 && still == 4 && over == 0,
                    "/fullcourse unlock: to 4 gave %s (%s slots), to 3 gave %s (%s slots), past the maximum gave %s", up, four, down, still, over);
        } catch (CommandSyntaxException e) {
            check(false, "/fullcourse unlock failed to parse: %s", e.getMessage());
        }
        check(!parses(c, "fullcourse unlock @s 4", c.player().createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS)),
                "/fullcourse unlock needs operator rights");
        Services.PLATFORM.setFoodState(c.player(), FoodState.EMPTY);
    }
}
