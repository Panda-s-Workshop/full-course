package com.panda.fullcourse.registry;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.platform.registry.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FullCourse.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> Services.PLATFORM.tabBuilder()
            .title(Component.translatable("itemGroup.fullcourse"))
            .icon(() -> new ItemStack(ModItems.COOKING_POT.get()))
            .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(h -> output.accept(h.get())))
            .build());

    private ModTabs() {}
}
