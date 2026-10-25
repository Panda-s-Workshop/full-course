package com.panda.fullcourse.food;

import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.logic.Tier;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a food is worth: its tier, the parts of its score for the tooltip, the families it gives and
 * whether it's a remedy (vanilla effects only, no meal slot). Computed on the server, synced to clients.
 *
 * @param steps the depth of its cheapest recipe chain
 * @param stations the distinct stations in that chain (the score counts the crock twice; this counts it once)
 * @param stationPoints what the stations add to the score: one each, the crock two
 * @param ingredients the distinct raw ingredients that chain starts from
 * @param ingredient some recipe makes it into another food: a raw food's tooltip says so only then
 */
public record Profile(Tier tier, int score, int steps, int stations, int stationPoints, int ingredients, List<Family> families, boolean remedy,
                      boolean ingredient) {
    public static final Profile NONE = new Profile(Tier.RAW, 0, 0, 0, 0, 0, List.of(), false, false);

    public static final StreamCodec<ByteBuf, Profile> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public Profile decode(ByteBuf buf) {
            Tier tier = Tier.ofStars(ByteBufCodecs.VAR_INT.decode(buf));
            int score = ByteBufCodecs.VAR_INT.decode(buf);
            int steps = ByteBufCodecs.VAR_INT.decode(buf);
            int stations = ByteBufCodecs.VAR_INT.decode(buf);
            int stationPoints = ByteBufCodecs.VAR_INT.decode(buf);
            int ingredients = ByteBufCodecs.VAR_INT.decode(buf);
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            List<Family> families = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Family f = Family.values()[Math.clamp(ByteBufCodecs.VAR_INT.decode(buf), 0, Family.values().length - 1)];
                if (!families.contains(f)) families.add(f);
            }
            boolean remedy = ByteBufCodecs.BOOL.decode(buf);
            boolean ingredient = ByteBufCodecs.BOOL.decode(buf);
            return new Profile(tier, score, steps, stations, stationPoints, ingredients, families, remedy, ingredient);
        }

        @Override
        public void encode(ByteBuf buf, Profile p) {
            ByteBufCodecs.VAR_INT.encode(buf, p.tier().stars());
            ByteBufCodecs.VAR_INT.encode(buf, p.score());
            ByteBufCodecs.VAR_INT.encode(buf, p.steps());
            ByteBufCodecs.VAR_INT.encode(buf, p.stations());
            ByteBufCodecs.VAR_INT.encode(buf, p.stationPoints());
            ByteBufCodecs.VAR_INT.encode(buf, p.ingredients());
            ByteBufCodecs.VAR_INT.encode(buf, p.families().size());
            for (Family f : p.families()) ByteBufCodecs.VAR_INT.encode(buf, f.ordinal());
            ByteBufCodecs.BOOL.encode(buf, p.remedy());
            ByteBufCodecs.BOOL.encode(buf, p.ingredient());
        }
    };

    public Profile {
        families = families.stream().distinct().toList();
    }

    /** Whether eating it fills a meal slot: tier 1 or more, and not a remedy. */
    public boolean givesMeal() {
        return tier != Tier.RAW && !remedy && !families.isEmpty();
    }
}
