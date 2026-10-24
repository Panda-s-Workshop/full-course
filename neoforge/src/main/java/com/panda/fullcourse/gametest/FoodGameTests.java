package com.panda.fullcourse.gametest;

import com.panda.fullcourse.checks.RuleChecks;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every {@link RuleChecks} check as a GameTest of the same name, each with a fresh mock player. */
final class FoodGameTests {
    private FoodGameTests() {}

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        for (String name : RuleChecks.CHECKS.keySet()) functions.register(name, () -> helper -> run(helper, name));
    }

    private static void run(GameTestHelper helper, String name) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            RuleChecks.run(name, player, helper.absolutePos(new BlockPos(1, 2, 1)));
        } catch (RuleChecks.CheckFailure e) {
            throw helper.assertionException(e.getMessage());
        } finally {
            player.level().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
