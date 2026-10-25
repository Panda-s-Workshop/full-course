package com.panda.fullcourse.food;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.JsonOps;
import com.panda.fullcourse.logic.Meals;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FoodStateTest {
    private static final Identifier BANQUET = Identifier.fromNamespaceAndPath("fullcourse", "roast_banquet");
    private static final Identifier CAKE = Identifier.fromNamespaceAndPath("minecraft", "cake");

    @Test
    void helpingsAreSavedAndLoaded() {
        FoodState s = new FoodState(Meals.empty(), 3, Set.of(CAKE), 1, Map.of(BANQUET, 1200L));
        var json = FoodState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
        assertEquals(s, FoodState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void aSaveFromBeforeHelpingsLoadsWithNone() {
        var json = com.google.gson.JsonParser.parseString("{\"nourish\":2,\"bonus_slots\":1}");
        assertEquals(Map.of(), FoodState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().helpings());
    }

    @Test
    void helpingsOutsideTheWindowAreDropped() {
        FoodState s = FoodState.EMPTY.withHelping(CAKE, 100, 6000).withHelping(BANQUET, 6099, 6000);
        assertEquals(Map.of(CAKE, 100L, BANQUET, 6099L), s.helpings());
        assertEquals(Map.of(BANQUET, 6100L), s.withHelping(BANQUET, 6100, 6000).helpings());
    }
}
