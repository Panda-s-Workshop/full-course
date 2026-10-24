package com.panda.fullcourse;

import com.mojang.logging.LogUtils;
import com.panda.fullcourse.platform.Services;
import com.panda.fullcourse.registry.ModBlockEntities;
import com.panda.fullcourse.registry.ModBlocks;
import com.panda.fullcourse.registry.ModItems;
import com.panda.fullcourse.registry.ModMenus;
import com.panda.fullcourse.registry.ModRecipes;
import com.panda.fullcourse.registry.ModTabs;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/** The mod's common side. Each loader module has its own entry point, which calls {@link #init()} first. */
public final class FullCourse {
    public static final String MODID = "fullcourse";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The classes whose static DeferredRegisters hold the mod's content. */
    private static final List<Class<?>> CONTENT = List.of(ModBlocks.class, ModBlockEntities.class, ModItems.class, ModMenus.class,
            ModRecipes.class, ModTabs.class);

    private FullCourse() {}

    /** Loads the content classes so every registration is queued before the loader registers them. */
    public static void init() {
        for (Class<?> c : CONTENT) {
            try {
                Class.forName(c.getName(), true, c.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        LOGGER.info("Full Course booting on {}", Services.PLATFORM.loaderName());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
