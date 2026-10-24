package com.panda.fullcourse.registry;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.kitchen.BrickOvenMenu;
import com.panda.fullcourse.kitchen.CookingPotMenu;
import com.panda.fullcourse.platform.registry.DeferredHolder;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FullCourse.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CookingPotMenu>> COOKING_POT = MENUS.register("cooking_pot",
            () -> new MenuType<>(CookingPotMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<BrickOvenMenu>> BRICK_OVEN = MENUS.register("brick_oven",
            () -> new MenuType<>(BrickOvenMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
