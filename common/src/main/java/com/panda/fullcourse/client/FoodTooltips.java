package com.panda.fullcourse.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.food.FoodProfiles;
import com.panda.fullcourse.food.FoodText;
import com.panda.fullcourse.food.Profile;
import com.panda.fullcourse.kitchen.RecipeHints;
import com.panda.fullcourse.platform.Services;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Food tooltips: {@link FoodText}'s lines under the item's name, with the recipe chain while Shift is held, and for the
 * mod's own items how they're made ({@link RecipeHints}). A station says how it's used, and with Shift how it's
 * crafted (lang keys {@code tooltip.fullcourse.<item>.use} and {@code .craft}).
 */
public final class FoodTooltips {
    /** Treats Shift as held, for tests: the keyboard can't be pressed from one. */
    public static boolean shiftDown;

    private FoodTooltips() {}

    public static void onTooltip(ItemStack stack, List<Component> lines) {
        if (stack.isEmpty() || lines.isEmpty() || !FoodProfiles.clientReady()) return; // a server without Full Course
        Minecraft mc = Minecraft.getInstance();
        boolean shift = shiftDown || mc.hasShiftDown();
        Profile p = FoodProfiles.client(stack.getItem());
        boolean food = FoodProfiles.isFood(stack.getItem()) || p != Profile.NONE;
        List<Component> made = new ArrayList<>(RecipeHints.lines(stack.getItem()));
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.getNamespace().equals(FullCourse.MODID)) {
            String key = "tooltip.fullcourse." + id.getPath();
            if (Language.getInstance().has(key + ".use")) lines.add(1, Component.translatable(key + ".use").withStyle(ChatFormatting.GRAY));
            if (Language.getInstance().has(key + ".craft")) made.add(Component.translatable(key + ".craft").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (food) {
            boolean tasted = mc.player != null
                    && Services.PLATFORM.foodState(mc.player).tasted().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            var component = stack.get(DataComponents.FOOD);
            lines.addAll(1, FoodText.lines(p, component == null ? 0 : component.nutrition(), shift, tasted));
        } else if (!made.isEmpty() && !shift) {
            lines.add(1, Component.translatable("tooltip.fullcourse.shift_recipe").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (shift) lines.addAll(made);
    }
}
