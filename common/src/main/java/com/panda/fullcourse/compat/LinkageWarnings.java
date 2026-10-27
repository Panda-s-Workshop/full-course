package com.panda.fullcourse.compat;

import com.panda.fullcourse.FullCourse;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reading another mod's class reflectively can fail with a {@link LinkageError} rather than an exception: on a
 * dedicated server, a class whose methods name client-only types (a block with a {@code PoseStack} overlay method)
 * throws {@link NoClassDefFoundError} as soon as its method signatures are resolved. The compat readers catch it, skip
 * that class, and say so once per class here.
 */
final class LinkageWarnings {
    private static final Set<Class<?>> WARNED = ConcurrentHashMap.newKeySet();

    private LinkageWarnings() {}

    static void once(Class<?> owner, String reading, LinkageError e) {
        if (WARNED.add(owner)) {
            FullCourse.LOGGER.warn("Couldn't read {} from {} (skipped, {}: {})", reading, owner.getName(), e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
