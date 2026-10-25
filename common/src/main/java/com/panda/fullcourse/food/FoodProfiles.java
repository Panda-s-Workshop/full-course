package com.panda.fullcourse.food;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panda.fullcourse.FullCourse;
import com.panda.fullcourse.FullCourseConfig;
import com.panda.fullcourse.compat.FarmersDelightCuts;
import com.panda.fullcourse.compat.RecipeFields;
import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.logic.Tier;
import com.panda.fullcourse.logic.TierScorer;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
    /**
     * Datapack overrides: {@code data/<ns>/fullcourse/food_profiles/<item path>.json} for the item {@code <ns>:<path>},
     * with any of {@code tier}, {@code families}, {@code remedy} and {@code serving_of} (the item id of the dish this is
     * a serving or slice of: a feast served from its block, a pie cut into slices).
     */
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

    /** {@code servingOf}: the dish this item is a serving or slice of; it is worth what that dish is worth. */
    private record ProfileOverride(Optional<Integer> tier, Optional<List<Family>> families, Optional<Boolean> remedy, Optional<Item> servingOf) {}

    private static Map<Item, Profile> compute(MinecraftServer mcServer) {
        Map<Item, List<TierScorer.Recipe<Item>>> rawCuts = new HashMap<>();
        Map<Item, List<TierScorer.Recipe<Item>>> byOutput = recipeGraph(mcServer, rawCuts);
        Map<Item, ProfileOverride> overrides = overrides(mcServer);
        // a dish served from a block (a feast, a pie cut into slices) is assembled: a step, though only the tagged placed
        // meals (our feasts, cake) are feasts
        Set<Item> servedFrom = new HashSet<>();
        for (ProfileOverride o : overrides.values()) o.servingOf().filter(dish -> !isFood(dish)).ifPresent(servedFrom::add);
        // putting the slices back together (four slices make a pie) is no way to make the dish
        overrides.forEach((serving, o) -> o.servingOf().ifPresent(dish -> byOutput.computeIfPresent(dish, (k, recipes) -> {
            List<TierScorer.Recipe<Item>> kept = recipes.stream().filter(r -> !onlyServingsOf(r, dish, overrides)).toList();
            return kept.isEmpty() ? null : kept;
        })));
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
            public List<TierScorer.Recipe<Item>> prep(Item item) {
                return rawCuts.getOrDefault(item, List.of());
            }

            @Override
            public boolean placed(Item item) {
                return item.builtInRegistryHolder().is(FoodTags.PLACED_MEALS);
            }

            @Override
            public boolean served(Item item) {
                return servedFrom.contains(item);
            }
        });
        int[] thresholds = FullCourseConfig.tierThresholds();
        Map<Item, Profile> out = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            boolean placed = item.builtInRegistryHolder().is(FoodTags.PLACED_MEALS);
            if (!isFood(item) && !placed) continue;
            ProfileOverride o = overrides.get(item);
            // a serving is rated as its dish: the dish's chain, groups and own override, under this item's override
            Item dish = o != null && o.servingOf().isPresent() ? o.servingOf().get() : item;
            ProfileOverride dishOverride = dish == item ? null : overrides.get(dish);
            TierScorer.Score<Item> score = scorer.score(dish);
            boolean remedy = o != null && o.remedy().isPresent() ? o.remedy().get() : item.builtInRegistryHolder().is(FoodTags.REMEDIES);
            Optional<Integer> setTier = o != null && o.tier().isPresent() ? o.tier() : dishOverride != null ? dishOverride.tier() : Optional.empty();
            Optional<List<Family>> setFamilies = o != null && o.families().isPresent() ? o.families()
                    : dishOverride != null ? dishOverride.families() : Optional.empty();
            // a remedy has no tier: its own effects and the heal, no meal, so no stars to imply one
            Tier tier = remedy || (setTier.isEmpty() && rawCuts.containsKey(dish)) ? Tier.RAW
                    : setTier.map(Tier::ofStars).orElseGet(() -> Tier.fromScore(score.score(), thresholds));
            if (setTier.isEmpty() && cutThenCooked(score) && tier.stars() > Tier.SIMPLE.stars()) tier = Tier.SIMPLE;
            List<Family> families;
            if (remedy) {
                families = List.of();
            } else if (setFamilies.isPresent()) {
                families = setFamilies.get();
            } else {
                families = TierScorer.families(dish, score.leaves(), FoodProfiles::groupsOf, tier.benefits());
                // a food whose ingredients belong to no group still counts: a good meal helps you heal
                if (families.isEmpty() && tier.benefits() > 0) families = List.of(Family.MENDING);
                // a soup always mends: Mending takes the weakest-counted place if the count left it out
                if (tier.benefits() > 0 && !families.contains(Family.MENDING) && dish.builtInRegistryHolder().is(FoodTags.SOUPS)) {
                    List<Family> withMending = new ArrayList<>(families.subList(0, families.size() - 1));
                    withMending.add(Family.MENDING);
                    families = List.copyOf(withMending);
                }
            }
            out.put(item, new Profile(tier, score.score(), score.depth(), score.stations().size(), score.leaves().size(), families, remedy));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Only cut and cooked (a fish fillet grilled, minced beef fried into a patty): an edible intermediate, rated with
     * plain cooked food at one star, though its two steps would score two.
     */
    private static boolean cutThenCooked(TierScorer.Score<Item> score) {
        return score.stations().containsKey("fire") && score.stations().size() == 2
                && score.stations().keySet().stream().anyMatch(s -> s.endsWith(":cutting"));
    }

    /** Every ingredient of {@code recipe} is a serving of {@code dish}. */
    private static boolean onlyServingsOf(TierScorer.Recipe<Item> recipe, Item dish, Map<Item, ProfileOverride> overrides) {
        for (Set<Item> slot : recipe.ingredients()) {
            for (Item i : slot) {
                ProfileOverride o = overrides.get(i);
                if (o == null || o.servingOf().filter(dish::equals).isEmpty()) return false;
            }
        }
        return true;
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

    /**
     * Every recipe of every type, as output item → recipes. A recipe that can't be placed is read from its own fields
     * instead (Farmer's Delight's cuts, {@link FarmersDelightCuts}; machine records, {@link RecipeFields}), or counted
     * and logged as unread. A cut that makes a raw ingredient of its own mod
     * (minced beef, bacon, rice) goes to {@code rawCuts} instead: the only way to get that item, so it counts toward
     * every dish made from it. A cut back to something obtained raw anyway (ham into porkchops) doesn't.
     */
    private static Map<Item, List<TierScorer.Recipe<Item>>> recipeGraph(MinecraftServer mcServer, Map<Item, List<TierScorer.Recipe<Item>>> rawCuts) {
        ContextMap context = SlotDisplayContext.fromLevel(mcServer.overworld());
        Map<Item, List<TierScorer.Recipe<Item>>> byOutput = new HashMap<>();
        Map<String, Integer> unread = new java.util.TreeMap<>();
        for (RecipeHolder<?> holder : mcServer.getRecipeManager().getRecipes()) {
            try {
                add(holder, context, byOutput, rawCuts, unread);
            } catch (RuntimeException e) {
                unread.merge(station(holder.value().getType()), 1, Integer::sum); // one mod's broken recipe isn't everyone's table
            }
        }
        if (!unread.isEmpty()) {
            FullCourse.LOGGER.info("Full Course couldn't read the ingredients of {} recipes ({}); a food made only by them rates as raw "
                    + "unless a food_profiles override rates it", unread.values().stream().mapToInt(Integer::intValue).sum(), unread);
        }
        return byOutput;
    }

    private static void add(RecipeHolder<?> holder, ContextMap context, Map<Item, List<TierScorer.Recipe<Item>>> byOutput,
                            Map<Item, List<TierScorer.Recipe<Item>>> rawCuts, Map<String, Integer> unread) {
        Recipe<?> recipe = holder.value();
        List<Ingredient> ingredients;
        ItemStack result;
        Optional<FarmersDelightCuts.Cut> cut = FarmersDelightCuts.read(recipe);
        if (cut.isPresent()) {
            ingredients = List.of(cut.get().input());
            result = cut.get().result();
        } else if (!recipe.placementInfo().isImpossibleToPlace()) {
            ingredients = recipe.placementInfo().ingredients();
            result = result(recipe, context);
        } else {
            Optional<RecipeFields.Read> fields = RecipeFields.read(recipe);
            if (fields.isEmpty()) {
                String type = station(recipe.getType());
                // vanilla's own unplaceable recipes (special crafting, smithing) make no food; other mods' may (Create
                // marks every machine recipe special)
                if (type.contains(":") && !type.startsWith("minecraft:")) unread.merge(type, 1, Integer::sum);
                return;
            }
            ingredients = fields.get().ingredients();
            result = fields.get().result();
        }
        if (result.isEmpty()) return;
        List<Set<Item>> slots = new ArrayList<>();
        Set<Ingredient> seen = new LinkedHashSet<>();
        for (Ingredient ingredient : ingredients) {
            if (!seen.add(ingredient)) continue; // the same ingredient twice is one ingredient
            Set<Item> alternatives = new LinkedHashSet<>();
            ingredient.items().forEach(h -> alternatives.add(h.value()));
            if (!alternatives.isEmpty()) slots.add(alternatives);
        }
        if (slots.isEmpty()) return;
        String station = station(recipe.getType());
        int weight = STATION_WEIGHTS.getOrDefault(station, 1);
        TierScorer.Recipe<Item> step = new TierScorer.Recipe<>(station, weight, List.copyOf(slots), result.getCount());
        Item made = result.getItem();
        // a cut of food (beef, a rice panicle), not of a wild plant's bush back into its crop
        boolean culinaryInput = slots.getFirst().stream().anyMatch(i -> isFood(i) || i.builtInRegistryHolder().is(FoodTags.CULINARY));
        boolean rawCut = cut.isPresent() && culinaryInput && made.builtInRegistryHolder().is(FoodTags.RAW_INGREDIENTS)
                && BuiltInRegistries.ITEM.getKey(made).getNamespace().equals(holder.id().identifier().getNamespace());
        (rawCut ? rawCuts : byOutput).computeIfAbsent(made, k -> new ArrayList<>()).add(step);
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
                Optional<Item> servingOf = json.has("serving_of")
                        ? BuiltInRegistries.ITEM.get(Identifier.parse(json.get("serving_of").getAsString())).map(Holder::value) : Optional.empty();
                out.put(item.get().value(), new ProfileOverride(tier, families, remedy, servingOf));
            } catch (Exception ex) {
                FullCourse.LOGGER.warn("Full Course: couldn't read food profile override {}", file, ex);
            }
        }
        return out;
    }
}
