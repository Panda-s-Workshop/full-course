package com.panda.fullcourse.food;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Tasting Journal: using it opens the journal screen on the client. The screen only shows what the player's
 * synced food state already holds, so the server does nothing; tasting counts with or without the journal.
 */
public class TastingJournalItem extends Item {
    /** Opens the screen; the loaders' client entry points set it, and a dedicated server never runs it. */
    private static Runnable opener = () -> {};

    public TastingJournalItem(Properties properties) {
        super(properties);
    }

    public static void setOpener(Runnable open) {
        opener = open;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) opener.run();
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }
}
