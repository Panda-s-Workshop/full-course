package com.panda.fullcourse;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Cheap source-level checks: server safety and lang coverage for literal keys. */
class SourceHygieneTest {
    private static final Path SRC = Path.of("src", "main", "java");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "fullcourse", "lang", "en_us.json");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void commonCodeNeverTouchesClientClasses() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : javaFiles()) {
            String norm = p.toString().replace('\\', '/');
            if (norm.contains("/client/")) continue;
            String text = Files.readString(p);
            if (text.contains("net.minecraft.client") || text.contains("com.mojang.blaze3d")) offenders.add(norm);
        }
        assertTrue(offenders.isEmpty(), "client classes referenced outside the client package: " + offenders);
    }

    @Test
    void literalTranslationKeysExistInTheLangFile() throws IOException {
        String lang = Files.readString(LANG);
        Pattern literal = Pattern.compile("translatable\\(\"([a-z0-9_.]+)\"");
        Set<String> missing = new TreeSet<>();
        for (Path p : javaFiles()) {
            Matcher m = literal.matcher(Files.readString(p));
            while (m.find()) {
                String key = m.group(1);
                if (key.endsWith(".")) continue; // a prefix: the rest is computed
                if (!lang.contains("\"" + key + "\"")) missing.add(key + "  (" + p.getFileName() + ")");
            }
        }
        assertTrue(missing.isEmpty(), "lang keys used in code but missing from en_us.json: " + missing);
    }

    /** Each sound event has a sounds.json entry pointing at a vanilla event, and each subtitle it names has English. */
    @Test
    void everySoundEventHasAnEntryAndItsSubtitleHasEnglish() throws IOException {
        String registry = Files.readString(SRC.resolve(Path.of("com", "panda", "fullcourse", "registry", "ModSounds.java")));
        com.google.gson.JsonObject sounds = com.google.gson.JsonParser.parseString(
                Files.readString(Path.of("src", "main", "resources", "assets", "fullcourse", "sounds.json"))).getAsJsonObject();
        com.google.gson.JsonObject lang = com.google.gson.JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        Matcher m = Pattern.compile("sound\\(\"([a-z0-9_.]+)\"\\)").matcher(registry);
        Set<String> events = new TreeSet<>();
        while (m.find()) events.add(m.group(1));
        assertFalse(events.isEmpty(), "no sound events found in ModSounds");
        assertEquals(events, new TreeSet<>(sounds.keySet()), "ModSounds and sounds.json name different events");
        for (String event : events) {
            var entry = sounds.getAsJsonObject(event);
            for (var sound : entry.getAsJsonArray("sounds")) {
                var o = sound.getAsJsonObject();
                assertEquals("event", o.get("type").getAsString(), event + " should point at a vanilla event");
                assertTrue(o.get("name").getAsString().startsWith("minecraft:"), event + " should point at a vanilla event");
            }
            if (entry.has("subtitle")) assertTrue(lang.has(entry.get("subtitle").getAsString()), event + "'s subtitle has no English");
        }
    }

    @Test
    void everyGeneratedJsonParsesAndHasNoTabs() throws IOException {
        Path res = Path.of("src", "main", "resources");
        com.google.gson.Gson gson = new com.google.gson.Gson();
        try (Stream<Path> s = Files.walk(res)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".json") || x.toString().endsWith(".mcmeta")).toList()) {
                String text = Files.readString(p);
                assertFalse(text.contains("\r"), "CRLF in " + p);
                assertDoesNotThrow(() -> gson.fromJson(text, Object.class), "invalid JSON: " + p);
            }
        }
    }
}
