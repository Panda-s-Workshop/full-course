package com.panda.fullcourse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodRules;
import com.panda.fullcourse.food.FoodState;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.food.Profile;
import com.panda.fullcourse.food.Tasting;
import com.panda.fullcourse.logic.Meals;
import com.panda.fullcourse.logic.Milestones;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.platform.Services;
import java.util.Collection;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /fullcourse tier [item]}: what a food scores and gives, as its tooltip with Shift held, for the item named or
 * the one in hand. Anyone may ask; pack makers use it to check their recipes.
 * {@code /fullcourse meals [player]}: a player's meals, slots, tasted dishes and next slot. Anyone may ask about
 * themselves; asking about someone else needs operator rights.
 * {@code /fullcourse unlock <players> <slots>} (operators): raises the players' meal slots to that many, up to the
 * config's maximum. It never takes slots away, since the journal would give earned ones back.
 */
public final class FullCourseCommand {
    private FullCourseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("fullcourse")
                .then(Commands.literal("tier")
                        .executes(FullCourseCommand::tierInHand)
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(c -> tier(c, ItemArgument.getItem(c, "item").createItemStack(1)))))
                .then(Commands.literal("meals")
                        .executes(c -> meals(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(c -> meals(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("unlock")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("slots", IntegerArgumentType.integer(1))
                                        .executes(c -> unlock(c, EntityArgument.getPlayers(c, "players"), IntegerArgumentType.getInteger(c, "slots")))))));
    }

    private static int tierInHand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return tier(c, c.getSource().getPlayerOrException().getMainHandItem());
    }

    private static int tier(CommandContext<CommandSourceStack> c, ItemStack stack) {
        CommandSourceStack source = c.getSource();
        Profile p = FoodProfiles.get(source.getServer(), stack.getItem());
        if (stack.isEmpty() || p == Profile.NONE) {
            source.sendFailure(Component.translatable("command.fullcourse.tier.not_food", stack.getHoverName()));
            return 0;
        }
        var food = stack.get(DataComponents.FOOD);
        source.sendSuccess(() -> stack.getHoverName().copy().withStyle(ChatFormatting.BOLD), false);
        for (Component line : FoodText.lines(p, food == null ? 0 : food.nutrition(), true, false)) source.sendSuccess(() -> line, false);
        return Math.max(1, p.tier().stars());
    }

    /** The meal list beside the inventory, as chat lines. Returns the number of meals held. */
    private static int meals(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        CommandSourceStack source = c.getSource();
        FoodState state = FoodRules.state(player);
        long now = player.level().getGameTime();
        var meals = state.meals().active();
        source.sendSuccess(() -> Component.translatable("command.fullcourse.meals.header", player.getDisplayName(), meals.size(), state.slots(),
                state.tasted().size()).withStyle(ChatFormatting.BOLD), false);
        if (meals.isEmpty()) source.sendSuccess(() -> Component.translatable("command.fullcourse.meals.none").withStyle(ChatFormatting.GRAY), false);
        for (Meals.Meal<Identifier> meal : meals) source.sendSuccess(() -> mealLine(meal, now), false);
        Milestones.Next next = Milestones.next(Tasting.count(state.tasted(), item -> FoodProfiles.get(source.getServer(), item)), state.bonusSlots(),
                FullCourseConfig.slotMilestones());
        if (next != Milestones.Next.NONE && state.slots() < FullCourseConfig.maxMealSlots()) {
            Component line = Component.translatable(next.needsFeast() ? "gui.fullcourse.meals.next_feast" : "gui.fullcourse.meals.next",
                    state.slots() + 1, Math.min(next.have(), next.need()), next.need(), "★".repeat(Tier.ofStars(next.stars()).stars()));
            source.sendSuccess(() -> line, false);
        }
        return meals.size();
    }

    /** "Rabbit Stew ★★★ · 19:59 · III · Vigor, Mending", in the tier's colour. */
    private static Component mealLine(Meals.Meal<Identifier> meal, long now) {
        Component name = BuiltInRegistries.ITEM.getValue(meal.dish()).getName(new ItemStack(BuiltInRegistries.ITEM.getValue(meal.dish())));
        Component families = Component.literal(meal.families().stream()
                .map(f -> Component.translatable("family.fullcourse." + f.key()).getString()).collect(Collectors.joining(", ")));
        String time = FoodText.duration((int) Math.min(Integer.MAX_VALUE, meal.remaining(now)));
        return Component.translatable("command.fullcourse.meals.meal", name, "★".repeat(meal.tier().stars()), time,
                Component.translatable("enchantment.level." + meal.level()), families).withStyle(Style.EMPTY.withColor(meal.tier().color() & 0xFFFFFF));
    }

    /** Raises each player to {@code slots} meal slots. Returns how many players gained a slot. */
    private static int unlock(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int slots) {
        CommandSourceStack source = c.getSource();
        int max = FullCourseConfig.maxMealSlots();
        if (slots > max) {
            source.sendFailure(Component.translatable("command.fullcourse.unlock.over_max", max));
            return 0;
        }
        int raised = 0;
        for (ServerPlayer player : players) {
            FoodState before = FoodRules.state(player);
            int bonus = Math.max(before.bonusSlots(), slots - FullCourseConfig.startingMealSlots());
            FoodState after = before.withBonusSlots(bonus);
            if (after.slots() > before.slots()) {
                Services.PLATFORM.setFoodState(player, after);
                raised++;
            }
            source.sendSuccess(() -> Component.translatable(after.slots() > before.slots() ? "command.fullcourse.unlock.done" : "command.fullcourse.unlock.already",
                    player.getDisplayName(), after.slots()), true);
        }
        return raised;
    }
}
