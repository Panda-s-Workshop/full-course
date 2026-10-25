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

    /**
     * The state syncs whole each second a heal runs (the nourish count is in it): a full journal of 100 dishes, five
     * meals and two helpings stay a few kilobytes, the cost DECISIONS 64 accepts.
     */
    @Test
    void aFullStateSyncsInAFewKilobytes() {
        java.util.Set<Identifier> tasted = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 100; i++) tasted.add(Identifier.fromNamespaceAndPath("somemod", "a_long_dish_name_number_" + i));
        java.util.List<Meals.Meal<Identifier>> meals = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            meals.add(new Meals.Meal<>(Identifier.fromNamespaceAndPath("fullcourse", "dish_" + i), com.panda.fullcourse.logic.Tier.GOURMET,
                    java.util.List.of(com.panda.fullcourse.logic.Family.BRAWN, com.panda.fullcourse.logic.Family.VIGOR, com.panda.fullcourse.logic.Family.MENDING),
                    3, 1000L, 40000L));
        }
        FoodState s = new FoodState(new Meals<>(meals), 8, tasted, 3, Map.of(BANQUET, 1200L, CAKE, 3000L));
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        FoodState.STREAM_CODEC.encode(buf, s);
        int bytes = buf.readableBytes();
        System.out.println("a full food state syncs in " + bytes + " bytes");
        org.junit.jupiter.api.Assertions.assertTrue(bytes < 6000, bytes + " bytes");
        assertEquals(s, FoodState.STREAM_CODEC.decode(buf));
    }

    /** A save holding more heal-over-time than a player can have waiting (an edited file) doesn't load it as is. */
    @Test
    void aSavedNourishBeyondTheCapIsNotLoaded() {
        var over = com.google.gson.JsonParser.parseString("{\"nourish\":" + (com.panda.fullcourse.logic.Recovery.MAX_NOURISH + 1) + "}");
        var parsed = FoodState.CODEC.parse(JsonOps.INSTANCE, over);
        org.junit.jupiter.api.Assertions.assertTrue(parsed.result().filter(s -> s.nourish() > com.panda.fullcourse.logic.Recovery.MAX_NOURISH).isEmpty(),
                "loaded " + parsed);
        var atCap = com.google.gson.JsonParser.parseString("{\"nourish\":" + com.panda.fullcourse.logic.Recovery.MAX_NOURISH + "}");
        assertEquals(com.panda.fullcourse.logic.Recovery.MAX_NOURISH, FoodState.CODEC.parse(JsonOps.INSTANCE, atCap).getOrThrow().nourish());
    }

    @Test
    void helpingsOutsideTheWindowAreDropped() {
        FoodState s = FoodState.EMPTY.withHelping(CAKE, 100, 6000).withHelping(BANQUET, 6099, 6000);
        assertEquals(Map.of(CAKE, 100L, BANQUET, 6099L), s.helpings());
        assertEquals(Map.of(BANQUET, 6100L), s.withHelping(BANQUET, 6100, 6000).helpings());
    }
}
