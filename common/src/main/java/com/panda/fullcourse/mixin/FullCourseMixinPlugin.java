package com.panda.fullcourse.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Adds the mixins into other mods only when that mod is there, so a pack without it gets no "class not found" warning. */
public final class FullCourseMixinPlugin implements IMixinConfigPlugin {
    private static final String FD_PIE = "vectorwing/farmersdelight/common/block/PieBlock.class";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return present(FD_PIE) ? List.of("FarmersDelightPieMixin") : List.of();
    }

    private static boolean present(String resource) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return FullCourseMixinPlugin.class.getClassLoader().getResource(resource) != null
                || context != null && context.getResource(resource) != null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
