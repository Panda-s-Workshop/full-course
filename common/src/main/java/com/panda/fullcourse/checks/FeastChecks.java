package com.panda.fullcourse.checks;

import static com.panda.fullcourse.checks.RuleChecks.check;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.food.Profile;
import com.panda.fullcourse.kitchen.FeastBlock;
import com.panda.fullcourse.logic.Meals;
import com.panda.fullcourse.logic.Milestones;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.registry.ModBlocks;
import com.panda.fullcourse.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Feasts, the Tasting Journal and the slot milestones checked on a real server. Part of {@link RuleChecks#CHECKS}
 * through {@link KitchenChecks}.
 */
final class FeastChecks {
    private FeastChecks() {}

    static void register(Map<String, Consumer<RuleChecks.Ctx>> checks) {
        checks.put("feast_serving_gives_meal", FeastChecks::feastServingGivesMeal);
        checks.put("feast_last_serving", FeastChecks::feastLastServing);
        checks.put("feast_untouched_drops", FeastChecks::feastUntouchedDrops);
        checks.put("feast_needs_ground", FeastChecks::feastNeedsGround);
        checks.put("feast_one_helping", FeastChecks::feastOneHelping);
        checks.put("feast_refuses_queasy", FeastChecks::feastRefusesQueasy);
        checks.put("slot_unlocks_at_milestone", FeastChecks::slotUnlocksAtMilestone);
        checks.put("journal_recipe", FeastChecks::journalRecipe);
        checks.put("tasting_advancements", FeastChecks::tastingAdvancements);
    }

    private static FoodState state(RuleChecks.Ctx c) {
        return FoodRules.state(c.player());
    }

    /** Places {@code state} on stone at the check's spot, runs {@code body}, and puts both blocks back. */
    private static void placed(RuleChecks.Ctx c, BlockState state, Runnable body) {
        BlockPos pos = c.spot();
        BlockState before = c.level().getBlockState(pos), below = c.level().getBlockState(pos.below());
        c.level().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        c.level().setBlock(pos, state, 3);
        try {
            body.run();
        } finally {
            c.level().setBlock(pos, before, 3);
            c.level().setBlock(pos.below(), below, 3);
        }
    }

    /** A right-click on the feast with {@code held} in hand, through the server's own handling of it. */
    private static void serve(RuleChecks.Ctx c, ItemStack held) {
        c.player().setItemInHand(InteractionHand.MAIN_HAND, held);
        c.player().gameMode.useItemOn(c.player(), c.level(), held, InteractionHand.MAIN_HAND, KitchenChecks.hit(c.spot()));
        c.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void serve(RuleChecks.Ctx c) {
        serve(c, ItemStack.EMPTY);
    }

    /** As if another player came to the table: a helping is per player. */
    private static void nextGuest(RuleChecks.Ctx c) {
        Services.PLATFORM.setFoodState(c.player(), FoodState.EMPTY);
    }

    private static int servings(RuleChecks.Ctx c) {
        BlockState s = c.level().getBlockState(c.spot());
        return s.getBlock() instanceof FeastBlock ? s.getValue(FeastBlock.SERVINGS) : -1;
    }

    // ---- feasts -------------------------------------------------------------------------------------------------

    /** A serving of the banquet is a five-star meal of its families at level IV for 30 minutes, heals 6, and one is gone. */
    private static void feastServingGivesMeal(RuleChecks.Ctx c) {
        placed(c, ModBlocks.ROAST_BANQUET.get().defaultBlockState(), () -> {
            check(servings(c) == 8, "a placed banquet serves 8, has %s", servings(c));
            serve(c, new ItemStack(Items.IRON_SWORD)); // whatever is in hand, as cake
            var meals = state(c).meals().active();
            check(meals.size() == 1 && meals.getFirst().dish().equals(FullCourse.id("roast_banquet")), "a serving gives a banquet meal: %s", meals);
            var meal = meals.getFirst();
            check(meal.tier() == Tier.FEAST && meal.level() == 4 && meal.end() - meal.start() == 30 * 60 * 20
                    && meal.families().stream().map(f -> f.key()).toList().equals(List.of("brawn", "vigor", "mending")),
                    "Brawn, Vigor, Mending IV for 30:00: %s", meal);
            check(state(c).nourish() == 6, "a serving heals 6, nourish %s", state(c).nourish());
            check(servings(c) == 7, "one serving gone, %s left", servings(c));
            check(state(c).tasted().contains(FullCourse.id("roast_banquet")), "the banquet counts as tasted");
            Item banquet = ModBlocks.ROAST_BANQUET.get().asItem();
            List<String> lines = FoodText.lines(FoodProfiles.get(c.server(), banquet), 6, false, false).stream().map(Component::getString).toList();
            check(lines.contains("Feast bonus: +1 level, one helping each"), "the tooltip says why the levels are IV: %s", lines);
        });
    }

    /** The last serving of the banquet leaves the platter, which serves nothing more; the tart's goes with its tin. */
    private static void feastLastServing(RuleChecks.Ctx c) {
        placed(c, ModBlocks.ROAST_BANQUET.get().defaultBlockState().setValue(FeastBlock.SERVINGS, 1), () -> {
            serve(c);
            check(servings(c) == 0, "the empty platter stays: %s", c.level().getBlockState(c.spot()));
            nextGuest(c);
            int meals = state(c).meals().active().size();
            long nourish = state(c).nourish();
            serve(c);
            check(state(c).meals().active().size() == meals && state(c).nourish() == nourish, "an empty platter serves nothing");
        });
        placed(c, ModBlocks.GLOW_BERRY_TART.get().defaultBlockState().setValue(FeastBlock.SERVINGS, 1), () -> {
            serve(c);
            check(c.level().getBlockState(c.spot()).isAir(), "the tart's last quarter takes the tin: %s", c.level().getBlockState(c.spot()));
            check(state(c).meals().active().stream().anyMatch(m -> m.dish().equals(FullCourse.id("glow_berry_tart"))), "and gives its meal: %s",
                    state(c).meals().active());
        });
    }

    /** An untouched feast breaks back into its item; a started one, or an empty platter, drops nothing. */
    private static void feastUntouchedDrops(RuleChecks.Ctx c) {
        for (var feast : List.of(ModBlocks.ROAST_BANQUET.get(), ModBlocks.HARVEST_PIE.get(), ModBlocks.GLOW_BERRY_TART.get())) {
            BlockState full = feast.defaultBlockState();
            List<ItemStack> drops = Block.getDrops(full, c.level(), c.spot(), null);
            check(drops.size() == 1 && drops.getFirst().is(feast.asItem()), "an untouched %s drops itself: %s", feast, drops);
            for (int n = 0; n < feast.servings(); n++) {
                List<ItemStack> started = Block.getDrops(full.setValue(FeastBlock.SERVINGS, n), c.level(), c.spot(), null);
                check(started.isEmpty(), "%s with %s servings drops nothing: %s", feast, n, started);
            }
        }
    }

    /** A feast needs a solid block under it: placed on air it can't stay, and losing its table breaks it. */
    private static void feastNeedsGround(RuleChecks.Ctx c) {
        BlockState pie = ModBlocks.HARVEST_PIE.get().defaultBlockState();
        placed(c, pie, () -> {
            check(pie.canSurvive(c.level(), c.spot()), "a pie stands on stone");
            c.level().setBlock(c.spot().below(), Blocks.AIR.defaultBlockState(), 3);
            check(!pie.canSurvive(c.level(), c.spot()), "a pie can't stand on air");
            check(c.level().getBlockState(c.spot()).isAir(), "taking the table away breaks the pie: %s", c.level().getBlockState(c.spot()));
        });
    }

    /**
     * Holding the use key takes one helping, not the table: a second right-click within five minutes is refused and
     * the servings stay; the next guest takes theirs; after five minutes the same player may take another.
     */
    private static void feastOneHelping(RuleChecks.Ctx c) {
        placed(c, ModBlocks.ROAST_BANQUET.get().defaultBlockState(), () -> {
            serve(c);
            serve(c);
            check(servings(c) == 7, "two right-clicks back to back take one serving, %s left", servings(c));
            nextGuest(c);
            serve(c);
            check(servings(c) == 6, "the next guest takes theirs, %s left", servings(c));
            var meals = state(c).meals().active();
            var later = new Meals.Meal<>(meals.getFirst().dish(), meals.getFirst().tier(), meals.getFirst().families(), meals.getFirst().level(),
                    meals.getFirst().start() - FeastBlock.HELPING_TICKS, meals.getFirst().end() - FeastBlock.HELPING_TICKS);
            Services.PLATFORM.setFoodState(c.player(), state(c).withMeals(new Meals<>(List.of(later))));
            serve(c);
            check(servings(c) == 5, "five minutes on, another helping, %s left", servings(c));
        });
    }

    /** A Queasy player would get no meal, so the feast turns them away and keeps the serving. */
    private static void feastRefusesQueasy(RuleChecks.Ctx c) {
        placed(c, ModBlocks.HARVEST_PIE.get().defaultBlockState(), () -> {
            c.player().addEffect(new MobEffectInstance(MobEffects.HUNGER, 200));
            serve(c);
            check(servings(c) == 6 && state(c).meals().isEmpty(), "Queasy: no serving taken, no meal: %s left", servings(c));
            c.player().removeAllEffects();
            serve(c);
            check(servings(c) == 5, "well again, a serving: %s left", servings(c));
        });
    }

    // ---- slots and the journal ----------------------------------------------------------------------------------

    /** The server's foods that give a meal of exactly {@code stars}, lowest score first. */
    private static List<Item> foods(RuleChecks.Ctx c, int stars) {
        List<Item> out = new ArrayList<>();
        for (Map.Entry<Item, Profile> e : FoodProfiles.all(c.server()).entrySet()) {
            if (e.getValue().givesMeal() && e.getValue().tier().stars() == stars) out.add(e.getKey());
        }
        out.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        return out;
    }

    private static void taste(RuleChecks.Ctx c, Item item) {
        FoodRules.onEatDish(c.player(), item, 1, c.t());
    }

    /**
     * Tasting by tier unlocks the slots: the third at the 8th dish of two stars or more, the fourth at the 15th of three
     * or more, the fifth at the 20th of three or more once a feast is among them, each with its advancement, and the
     * dish that earns a slot already has it. Tasting a dish twice counts once.
     */
    private static void slotUnlocksAtMilestone(RuleChecks.Ctx c) {
        for (String key : List.of("slot_3", "slot_4", "slot_5")) {
            c.player().getAdvancements().revoke(c.server().getAdvancements().get(FullCourse.id(key)), "done");
        }
        List<Item> prepared = foods(c, 2);
        List<Item> hearty = new ArrayList<>(foods(c, 3));
        hearty.addAll(foods(c, 4));
        List<Item> feasts = foods(c, 5);
        check(prepared.size() >= Milestones.THIRD && hearty.size() >= Milestones.FIFTH - 1 && !feasts.isEmpty(),
                "enough dishes to reach every slot: %s two-star, %s three-or-four-star, %s feasts", prepared.size(), hearty.size(), feasts.size());
        for (int i = 0; i < Milestones.THIRD - 1; i++) taste(c, prepared.get(i));
        taste(c, prepared.get(0));
        check(state(c).bonusSlots() == 0 && state(c).slots() == 2, "7 dishes (one twice): still 2 slots, %s", state(c).slots());
        taste(c, prepared.get(Milestones.THIRD - 1));
        check(state(c).slots() == 3 && KitchenChecks.done(c, "slot_3"), "the 8th two-star dish opens the third slot: %s", state(c).slots());
        check(state(c).meals().active().size() == 3, "the dish that earned it fills it: %s meals", state(c).meals().active().size());
        for (int i = 0; i < Milestones.FOURTH - 1; i++) taste(c, hearty.get(i));
        check(state(c).slots() == 3, "14 three-star dishes: still 3 slots");
        taste(c, hearty.get(Milestones.FOURTH - 1));
        check(state(c).slots() == 4 && KitchenChecks.done(c, "slot_4"), "the 15th three-star dish opens the fourth slot: %s", state(c).slots());
        taste(c, feasts.getFirst());
        int heartyNow = Milestones.FOURTH + 1; // a feast is three stars or more too
        for (int i = Milestones.FOURTH; heartyNow < Milestones.FIFTH - 1; i++, heartyNow++) taste(c, hearty.get(i));
        check(state(c).slots() == 4 && !KitchenChecks.done(c, "slot_5"), "19 three-star dishes and a feast: still 4 slots");
        taste(c, hearty.get(Milestones.FIFTH - 2));
        check(state(c).slots() == 5 && KitchenChecks.done(c, "slot_5"), "the 20th with a feast opens the fifth slot: %s", state(c).slots());
    }

    /** A book and any simple dish (a fried egg, vanilla bread) make the journal; a raw food doesn't. */
    private static void journalRecipe(RuleChecks.Ctx c) {
        Item journal = ModItems.item("tasting_journal");
        for (Item food : List.of(ModItems.item("fried_egg"), Items.BREAD, Items.COOKED_COD)) {
            ItemStack out = craft(c, new ItemStack(Items.BOOK), new ItemStack(food));
            check(out.is(journal), "a book and %s make the journal: %s", food, out);
        }
        check(craft(c, new ItemStack(Items.BOOK), new ItemStack(Items.BEEF)).isEmpty(), "a book and raw beef make nothing");
        check(new ItemStack(journal).getMaxStackSize() == 1, "the journal doesn't stack");
    }

    private static ItemStack craft(RuleChecks.Ctx c, ItemStack a, ItemStack b) {
        CraftingInput input = CraftingInput.of(2, 1, List.of(a, b));
        return c.server().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, c.level())
                .map(h -> h.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /** Four stars grant Gourmet, a feast Feast for All; tasting every one-star dish of the mod, Simple Pleasures. */
    private static void tastingAdvancements(RuleChecks.Ctx c) {
        for (String key : List.of("gourmet", "first_feast", "taste_simple", "taste_all")) {
            c.player().getAdvancements().revoke(c.server().getAdvancements().get(FullCourse.id(key)), "done");
        }
        taste(c, ModItems.item("vegetable_stew"));
        check(!KitchenChecks.done(c, "gourmet"), "a three-star stew isn't Gourmet");
        taste(c, ModItems.item("apple_pie"));
        check(KitchenChecks.done(c, "gourmet") && !KitchenChecks.done(c, "first_feast"), "apple pie is Gourmet, not a feast");
        taste(c, ModBlocks.HARVEST_PIE.get().asItem());
        check(KitchenChecks.done(c, "first_feast"), "a feast serving grants Feast for All");
        List<Item> simple = ModItems.dishes().stream().filter(d -> FoodProfiles.get(c.server(), d).tier() == Tier.SIMPLE).toList();
        check(!simple.isEmpty(), "the mod has one-star dishes");
        for (int i = 0; i < simple.size() - 1; i++) taste(c, simple.get(i));
        check(!KitchenChecks.done(c, "taste_simple"), "all but one one-star dish: not yet");
        taste(c, simple.getLast());
        check(KitchenChecks.done(c, "taste_simple") && !KitchenChecks.done(c, "taste_all"), "every one-star dish: Simple Pleasures");
        for (Item dish : ModItems.dishes()) taste(c, dish);
        check(KitchenChecks.done(c, "taste_all"), "every dish of the mod: Connoisseur");
    }
}
