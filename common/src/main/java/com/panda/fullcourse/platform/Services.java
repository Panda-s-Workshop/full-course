package com.panda.fullcourse.platform;

import java.util.ServiceLoader;

/** Finds the loader's implementations (META-INF/services in each loader module). */
public final class Services {
    public static final FullCoursePlatform PLATFORM = load(FullCoursePlatform.class);

    private Services() {}

    private static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type.getSimpleName() + " for this mod loader"));
    }
}
