package com.panda.fullcourse.food;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.logic.TierScorer;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Every food's {@link Profile}, from the server's recipes, tags and datapack overrides. Computed lazily
 * on the server and again whenever the recipe manager changes (a {@code /reload}); the client keeps the copy the server
 * sent ({@link #setClient}).
 */
public final class FoodProfiles {
    /** Datapack overrides: {@code data/<ns>/fullcourse/food_profiles/<item path>.json} for the item {@code <ns>:<path>}. */
    public static final String OVERRIDES = "fullcourse/food_profiles";
    /** The crock counts double: patience is effort. */
    private static final Map<String, Integer> STATION_WEIGHTS = Map.of("fullcourse:fermenting", 2);

    private static RecipeManager computedFor;
    private static Map<Item, Profile> server = Map.of();
    private static Map<Item, Profile> client = Map.of();
    /** The server sent its table: it runs Full Course. Without it the client shows vanilla's food bar and tooltips. */
    private static boolean clientReady;

    private FoodProfiles() {}

    /** The profile on the server: computed for the current recipes if they changed. */
    public static Profile get(MinecraftServer mcServer, Item item) {
        return all(mcServer).getOrDefault(item, Profile.NONE);
    }

    public static synchronized Map<Item, Profile> all(MinecraftServer mcServer) {
        RecipeManager recipes = mcServer.getRecipeManager();
        if (recipes != computedFor) {
            server = compute(mcServer);
            computedFor = recipes;
            FullCourse.LOGGER.info("Full Course rated {} foods", server.size());
        }
        return server;
    }

    /** Forget the server table (the server stopped; an integrated server restarts in the same JVM). */
    public static synchronized void reset() {
        computedFor = null;
        server = Map.of();
    }

    /** The client's copy: what the server sent last. */
    public static Profile client(Item item) {
        return client.getOrDefault(item, Profile.NONE);
    }

    public static void setClient(Map<Item, Profile> profiles) {
        client = Map.copyOf(profiles);
        clientReady = true;
    }

    public static boolean clientReady() {
        return clientReady;
    }

    /** Disconnected: forget the last server's table. */
    public static void clearClient() {
        client = Map.of();
        clientReady = false;
    }

    public static Map<Item, Profile> clientAll() {
        return client;
    }

    // ---- computing ---------------------------------------------------------------------------------------------

    private record ProfileOverride(Optional<Integer> tier, Optional<List<Family>> families, Optional<Boolean> remedy) {}

    private static Map<Item, Profile> compute(MinecraftServer mcServer) {
        Map<Item, List<TierScorer.Recipe<Item>>> byOutput = recipeGraph(mcServer);
        TierScorer<Item> scorer = new TierScorer<>(new TierScorer.Graph<>() {
            @Override
            public List<TierScorer.Recipe<Item>> recipesFor(Item item) {
                return byOutput.getOrDefault(item, List.of());
            }

            @Override
            public boolean culinary(Item item) {
                return isFood(item) || item.builtInRegistryHolder().is(FoodTags.CULINARY);
            }

            @Override
            public boolean container(Item item) {
                return item.builtInRegistryHolder().is(FoodTags.CONTAINERS);
            }

            @Override
            public boolean raw(Item item) {
                return item.builtInRegistryHolder().is(FoodTags.RAW_INGREDIENTS);
            }

            @Override
            public boolean placed(Item item) {
                return item.builtInRegistryHolder().is(FoodTags.PLACED_MEALS);
            }
        });
        Map<Item, ProfileOverride> overrides = overrides(mcServer);
        int[] thresholds = FullCourseConfig.tierThresholds();
        Map<Item, Profile> out = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            boolean placed = item.builtInRegistryHolder().is(FoodTags.PLACED_MEALS);
            if (!isFood(item) && !placed) continue;
            TierScorer.Score<Item> score = scorer.score(item);
            ProfileOverride o = overrides.get(item);
            boolean remedy = o != null && o.remedy().isPresent() ? o.remedy().get() : item.builtInRegistryHolder().is(FoodTags.REMEDIES);
            // a remedy has no tier: its own effects and the heal, no meal, so no stars to imply one
            Tier tier = remedy ? Tier.RAW
                    : o != null && o.tier().isPresent() ? Tier.ofStars(o.tier().get()) : Tier.fromScore(score.score(), thresholds);
            List<Family> families;
            if (remedy) {
                families = List.of();
            } else if (o != null && o.families().isPresent()) {
                families = o.families().get();
            } else {
                families = TierScorer.families(item, score.leaves(), FoodProfiles::groupsOf, tier.benefits());
                // a food whose ingredients belong to no group still counts: a good meal helps you heal (pillar 4)
                if (families.isEmpty() && tier.benefits() > 0) families = List.of(Family.MENDING);
                // a soup always mends: Mending takes the weakest-counted place if the count left it out
                if (tier.benefits() > 0 && !families.contains(Family.MENDING) && item.builtInRegistryHolder().is(FoodTags.SOUPS)) {
                    List<Family> withMending = new ArrayList<>(families.subList(0, families.size() - 1));
                    withMending.add(Family.MENDING);
                    families = List.copyOf(withMending);
                }
            }
            out.put(item, new Profile(tier, score.score(), score.depth(), score.stationWeight(), score.leaves().size(), families, remedy));
        }
        return Collections.unmodifiableMap(out);
    }

    static Set<Family> groupsOf(Item item) {
        Holder<Item> holder = item.builtInRegistryHolder();
        Set<Family> set = EnumSet.noneOf(Family.class);
        for (Map.Entry<Family, net.minecraft.tags.TagKey<Item>> e : FoodTags.GROUPS.entrySet()) {
            if (holder.is(e.getValue())) set.add(e.getKey());
        }
        return set;
    }

    public static boolean isFood(Item item) {
        return item.components().has(DataComponents.FOOD);
    }

    /** Every recipe of every type, as output item → recipes; recipes that can't be placed or have no result are skipped. */
    private static Map<Item, List<TierScorer.Recipe<Item>>> recipeGraph(MinecraftServer mcServer) {
        ContextMap context = SlotDisplayContext.fromLevel(mcServer.overworld());
        Map<Item, List<TierScorer.Recipe<Item>>> byOutput = new HashMap<>();
        for (RecipeHolder<?> holder : mcServer.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            List<Ingredient> ingredients;
            try {
                if (recipe.placementInfo().isImpossibleToPlace()) continue;
                ingredients = recipe.placementInfo().ingredients();
            } catch (RuntimeException e) {
                continue; // a mod's recipe that can't describe itself
            }
            ItemStack result = result(recipe, context);
            if (result.isEmpty()) continue;
            List<Set<Item>> slots = new ArrayList<>();
            Set<Ingredient> seen = new LinkedHashSet<>();
            for (Ingredient ingredient : ingredients) {
                if (!seen.add(ingredient)) continue; // the same ingredient twice is one ingredient
                Set<Item> alternatives = new LinkedHashSet<>();
                ingredient.items().forEach(h -> alternatives.add(h.value()));
                if (!alternatives.isEmpty()) slots.add(alternatives);
            }
            if (slots.isEmpty()) continue;
            String station = station(recipe.getType());
            int weight = STATION_WEIGHTS.getOrDefault(station, 1);
            byOutput.computeIfAbsent(result.getItem(), k -> new ArrayList<>())
                    .add(new TierScorer.Recipe<>(station, weight, List.copyOf(slots), result.getCount()));
        }
        return byOutput;
    }

    private static ItemStack result(Recipe<?> recipe, ContextMap context) {
        try {
            for (RecipeDisplay display : recipe.display()) {
                ItemStack stack = display.result().resolveForFirstStack(context);
                if (!stack.isEmpty()) return stack;
            }
        } catch (RuntimeException ignored) {
            // fall through: no usable result
        }
        return ItemStack.EMPTY;
    }

    /** Smelting, smoking, blasting and campfire cooking are one station: fire. Every other recipe type is its own. */
    static String station(RecipeType<?> type) {
        if (type == RecipeType.SMELTING || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING || type == RecipeType.BLASTING) return "fire";
        if (type == RecipeType.CRAFTING) return "crafting";
        Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? "unknown" : id.toString();
    }

    private static Map<Item, ProfileOverride> overrides(MinecraftServer mcServer) {
        Map<Item, ProfileOverride> out = new HashMap<>();
        for (Map.Entry<Identifier, Resource> e : mcServer.getResourceManager()
                .listResources(OVERRIDES, id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            String path = file.getPath().substring(OVERRIDES.length() + 1, file.getPath().length() - ".json".length());
            Identifier itemId = Identifier.fromNamespaceAndPath(file.getNamespace(), path);
            Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(itemId);
            if (item.isEmpty()) continue; // an override for a mod that isn't installed
            try (Reader reader = e.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Optional<Integer> tier = json.has("tier") ? Optional.of(Math.clamp(json.get("tier").getAsInt(), 0, 5)) : Optional.empty();
                Optional<Boolean> remedy = json.has("remedy") ? Optional.of(json.get("remedy").getAsBoolean()) : Optional.empty();
                Optional<List<Family>> families = Optional.empty();
                if (json.has("families")) {
                    List<Family> list = new ArrayList<>();
                    for (JsonElement el : json.getAsJsonArray("families")) Family.byKey(el.getAsString()).ifPresent(list::add);
                    families = Optional.of(List.copyOf(list));
                }
                out.put(item.get().value(), new ProfileOverride(tier, families, remedy));
            } catch (Exception ex) {
                FullCourse.LOGGER.warn("Full Course: couldn't read food profile override {}", file, ex);
            }
        }
        return out;
    }
}
