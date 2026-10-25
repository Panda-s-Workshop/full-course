package com.panda.fullcourse.kitchen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.panda.fullcourse.FullCourse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Onions in village house chests, for a loader that adds loot in code (Fabric). NeoForge reads the same two files as
 * data: {@code loot_modifiers/village_onions.json} names the chests and the table it adds, {@code
 * loot_table/chests/village_onions.json} is that table.
 */
public final class VillageOnions {
    private static final String MODIFIER = "/data/" + FullCourse.MODID + "/loot_modifiers/village_onions.json";
    private static Set<Identifier> chests;

    private VillageOnions() {}

    /** The chests' loot tables. */
    public static synchronized Set<Identifier> chests() {
        if (chests == null) {
            Set<Identifier> out = new HashSet<>();
            for (JsonElement term : json(MODIFIER).getAsJsonObject().getAsJsonObject("condition").getAsJsonArray("terms")) {
                out.add(Identifier.parse(term.getAsJsonObject().get("loot_table_id").getAsString()));
            }
            chests = Set.copyOf(out);
        }
        return chests;
    }

    /** The table each of them gets as one more pool. */
    public static LootTable table(HolderLookup.Provider registries) {
        Identifier id = Identifier.parse(json(MODIFIER).getAsJsonObject().get("table").getAsString());
        JsonElement table = json("/data/" + id.getNamespace() + "/loot_table/" + id.getPath() + ".json");
        return LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), table).getOrThrow();
    }

    private static JsonElement json(String path) {
        try (InputStream in = VillageOnions.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
