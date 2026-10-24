package com.panda.fullcourse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.food.Profile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /fullcourse tier [item]}: what a food scores and gives, as its tooltip with Shift held,
 * for the item named or the one in hand. Anyone may ask; pack makers use it to check their recipes.
 */
public final class FullCourseCommand {
    private FullCourseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("fullcourse")
                .then(Commands.literal("tier")
                        .executes(FullCourseCommand::tierInHand)
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(c -> tier(c, ItemArgument.getItem(c, "item").createItemStack(1))))));
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
}
