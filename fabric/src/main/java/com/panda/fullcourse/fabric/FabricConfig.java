package com.panda.fullcourse.fabric;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.PropertiesConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's settings on Fabric: config/fullcourse.properties, written with the defaults on first start. Unlike
 * NeoForge's synced config it is read once at startup, on each side from its own file; a server sends its values to
 * the players who join.
 */
final class FabricConfig {
    private FabricConfig() {}

    static PropertiesConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("fullcourse.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file)) {
                p.load(in);
                // keys a newer version added go at the end, with their defaults, so the file lists every setting
                String missing = PropertiesConfig.missingLines(p, PropertiesConfig.defaults(), PropertiesConfig.HEADER);
                if (!missing.isEmpty()) {
                    Files.writeString(file, System.lineSeparator() + missing, java.nio.file.StandardOpenOption.APPEND);
                    p.load(new java.io.StringReader(missing));
                }
            } catch (IOException e) {
                FullCourse.LOGGER.warn("Couldn't read {}, using the defaults", file, e);
            }
        } else {
            p = PropertiesConfig.defaults();
            try (Writer out = Files.newBufferedWriter(file)) {
                p.store(out, PropertiesConfig.HEADER);
            } catch (IOException e) {
                FullCourse.LOGGER.warn("Couldn't write {}", file, e);
            }
        }
        return new PropertiesConfig(p);
    }
}
