package com.panda.fullcourse.fabric.client;

import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.client.ClientOptions;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Each player's display settings on Fabric: config/fullcourse-client.properties, written with the defaults on first start. */
final class FabricClientConfig {
    private FabricClientConfig() {}

    static ClientOptions.Values load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("fullcourse-client.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file)) {
                p.load(in);
            } catch (IOException e) {
                FullCourse.LOGGER.warn("Couldn't read {}, using the defaults", file, e);
            }
        } else {
            p = ClientOptions.defaults();
            try (Writer out = Files.newBufferedWriter(file)) {
                p.store(out, ClientOptions.HEADER);
            } catch (IOException e) {
                FullCourse.LOGGER.warn("Couldn't write {}", file, e);
            }
        }
        return ClientOptions.fromProperties(p);
    }
}
