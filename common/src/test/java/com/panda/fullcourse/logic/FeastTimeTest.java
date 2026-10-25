package com.panda.fullcourse.logic;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A feast must take longer to make than five one-star meals last. Measured over the mod's recipes as the shortest
 * wait from raw ingredients, with the stations of a chain running side by side: each recipe adds its station's time to
 * the slowest of its ingredients.
 */
class FeastTimeTest {
    private static final Path RECIPES = Path.of("src", "main", "resources", "data", "fullcourse", "recipe");
    private static final List<String> FEASTS = List.of("fullcourse:roast_banquet", "fullcourse:harvest_pie", "fullcourse:glow_berry_tart");
    /** Vanilla foods the mod's recipes take that come off a smoker. */
    private static final Set<String> SMOKED = Set.of("minecraft:cooked_chicken", "minecraft:cooked_beef", "minecraft:cooked_porkchop",
            "minecraft:cooked_mutton", "minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:baked_potato");

    private record Step(int ticks, List<List<String>> ingredients) {}

    private final Map<String, List<Step>> byResult = new HashMap<>();
    private final Map<String, Integer> memo = new HashMap<>();
    private final Set<String> onStack = new HashSet<>();

    @Test
    void everyFeastTakesLongerThanFiveSimpleMealsLast() throws IOException {
        load();
        int floor = Tier.SIMPLE.minutes() * 5 * 60 * 20;
        for (String feast : FEASTS) {
            assertTrue(byResult.containsKey(feast), feast + " has a recipe");
            int ticks = ticks(feast);
            assertTrue(ticks >= floor, feast + " takes " + ticks + " ticks to make, under " + floor);
        }
    }

    private void load() throws IOException {
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!recipe.has("result")) continue;
                JsonElement result = recipe.get("result");
                String id = result.isJsonObject() ? result.getAsJsonObject().get("id").getAsString() : result.getAsString();
                List<List<String>> ingredients = new ArrayList<>();
                if (recipe.has("ingredients")) recipe.getAsJsonArray("ingredients").forEach(e -> ingredients.add(alternatives(e)));
                if (recipe.has("ingredient")) ingredients.add(alternatives(recipe.get("ingredient")));
                if (recipe.has("key")) recipe.getAsJsonObject("key").entrySet().forEach(e -> ingredients.add(alternatives(e.getValue())));
                byResult.computeIfAbsent(id, k -> new ArrayList<>()).add(new Step(time(recipe), ingredients));
            }
        }
    }

    private static List<String> alternatives(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e.isJsonArray()) e.getAsJsonArray().forEach(a -> out.add(a.getAsString()));
        else out.add(e.getAsString());
        return out;
    }

    private static int time(JsonObject recipe) {
        if (recipe.has("time")) return recipe.get("time").getAsInt();
        if (recipe.has("cookingtime")) return recipe.get("cookingtime").getAsInt();
        return switch (recipe.get("type").getAsString()) {
            case "fullcourse:cooking" -> 200;
            case "fullcourse:baking" -> 300;
            case "fullcourse:fermenting" -> 12000;
            default -> 0; // crafting, the board's cuts
        };
    }

    /** The shortest wait to make {@code item}: raw items, tags and recipe loops count as ready. */
    private int ticks(String item) {
        if (memo.containsKey(item)) return memo.get(item);
        if (SMOKED.contains(item)) return 100;
        List<Step> steps = byResult.get(item);
        if (steps == null || !onStack.add(item)) return 0;
        int best = Integer.MAX_VALUE;
        for (Step step : steps) {
            int slowest = 0;
            for (List<String> alternatives : step.ingredients()) {
                int fastest = Integer.MAX_VALUE;
                for (String alt : alternatives) fastest = Math.min(fastest, alt.startsWith("#") ? 0 : ticks(alt));
                slowest = Math.max(slowest, fastest);
            }
            best = Math.min(best, step.ticks() + slowest);
        }
        onStack.remove(item);
        memo.put(item, best);
        return best;
    }
}
