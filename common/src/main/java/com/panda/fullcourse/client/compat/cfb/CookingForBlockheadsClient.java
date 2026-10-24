package com.panda.fullcourse.client.compat.cfb;

import net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI;

/**
 * Cooking for Blockheads on the client: adds {@link TierSortButton} after the mod's own sort buttons. The loaders call
 * this only when the mod is installed; nothing in its signature names the mod's classes, so they compile without it.
 */
public final class CookingForBlockheadsClient {
    private CookingForBlockheadsClient() {}

    public static void register() {
        CookingForBlockheadsAPI.addSortButton(new TierSortButton());
    }
}
