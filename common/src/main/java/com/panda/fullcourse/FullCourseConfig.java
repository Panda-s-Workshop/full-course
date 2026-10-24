package com.panda.fullcourse;

import com.panda.fullcourse.logic.Tier;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** The mod's settings. Each loader keeps them in its own config system and plugs it in with {@link #use}. */
public final class FullCourseConfig {
    /** Where the values come from. The defaults apply until the loader's config is loaded. */
    public interface Source {
        /** Keep vanilla hunger and regeneration; the mod only adds meal benefits. */
        boolean keepVanillaHunger();

        /** Seconds without damage before Recovery starts. */
        int recoveryDelaySeconds();

        /** Ticks between Recovery heals without Mending. */
        int recoveryIntervalTicks();

        /** Meal slots a new player has. */
        int startingMealSlots();

        /** The most meal slots the journal milestones can unlock. */
        int maxMealSlots();

        /** Whether held meals survive death. */
        boolean keepMealsOnDeath();

        /** Multiplies every benefit duration. */
        double durationMultiplier();

        /** Lowest scores of tiers 1..5. */
        int[] tierThresholds();
    }

    public static final Source DEFAULTS = new Source() {
        @Override public boolean keepVanillaHunger() { return false; }
        @Override public int recoveryDelaySeconds() { return 8; }
        @Override public int recoveryIntervalTicks() { return 80; }
        @Override public int startingMealSlots() { return 2; }
        @Override public int maxMealSlots() { return 5; }
        @Override public boolean keepMealsOnDeath() { return false; }
        @Override public double durationMultiplier() { return 1.0; }
        @Override public int[] tierThresholds() { return Tier.DEFAULT_THRESHOLDS.clone(); }
    };

    /** The values a server sends its clients, so HUD, slots and tooltips match the server's. */
    public record Snapshot(boolean keepVanillaHunger, int recoveryDelaySeconds, int recoveryIntervalTicks, int startingMealSlots,
                           int maxMealSlots, boolean keepMealsOnDeath, double durationMultiplier, int[] tierThresholds) implements Source {
        public static final StreamCodec<ByteBuf, Snapshot> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Snapshot decode(ByteBuf buf) {
                boolean keep = buf.readBoolean();
                int delay = buf.readInt(), interval = buf.readInt(), start = buf.readInt(), max = buf.readInt();
                boolean keepMeals = buf.readBoolean();
                double multiplier = buf.readDouble();
                int[] thresholds = new int[Math.min(buf.readByte(), 5)];
                for (int i = 0; i < thresholds.length; i++) thresholds[i] = buf.readInt();
                return new Snapshot(keep, delay, interval, start, max, keepMeals, multiplier, thresholds);
            }

            @Override
            public void encode(ByteBuf buf, Snapshot s) {
                buf.writeBoolean(s.keepVanillaHunger()).writeInt(s.recoveryDelaySeconds()).writeInt(s.recoveryIntervalTicks())
                        .writeInt(s.startingMealSlots()).writeInt(s.maxMealSlots()).writeBoolean(s.keepMealsOnDeath())
                        .writeDouble(s.durationMultiplier());
                int[] t = s.tierThresholds();
                buf.writeByte(Math.min(t.length, 5));
                for (int i = 0; i < Math.min(t.length, 5); i++) buf.writeInt(t[i]);
            }
        };

        public static Snapshot of(Source s) {
            return new Snapshot(s.keepVanillaHunger(), s.recoveryDelaySeconds(), s.recoveryIntervalTicks(), s.startingMealSlots(),
                    s.maxMealSlots(), s.keepMealsOnDeath(), s.durationMultiplier(), s.tierThresholds().clone());
        }
    }

    private static Source local = DEFAULTS;
    /** While connected: the server's values, which win over this side's own file. */
    private static Source fromServer;
    private static Source source = DEFAULTS;

    private FullCourseConfig() {}

    public static void use(Source loaderConfig) {
        local = loaderConfig;
        source = fromServer != null ? fromServer : local;
    }

    /** This side's values, to send to a client. */
    public static Snapshot snapshot() {
        return Snapshot.of(source);
    }

    /** On the client: the server's values arrived. In single player they are the same values. */
    public static void useServer(Snapshot values) {
        fromServer = values;
        source = values;
    }

    /**
     * Runs {@code body} with {@code values} in place of both this side's file and the server's, then puts the old ones
     * back. GameTests check each key this way; in play the file is the source.
     */
    public static void with(Source values, Runnable body) {
        Source was = source;
        source = values;
        try {
            body.run();
        } finally {
            source = was;
        }
    }

    /** On the client: disconnected, back to this side's own file. */
    public static void clearServer() {
        fromServer = null;
        source = local;
    }

    public static boolean keepVanillaHunger() {
        return source.keepVanillaHunger();
    }

    public static int recoveryDelayTicks() {
        return source.recoveryDelaySeconds() * 20;
    }

    public static int recoveryIntervalTicks() {
        return source.recoveryIntervalTicks();
    }

    public static int startingMealSlots() {
        return source.startingMealSlots();
    }

    public static int maxMealSlots() {
        return Math.max(source.startingMealSlots(), source.maxMealSlots());
    }

    public static boolean keepMealsOnDeath() {
        return source.keepMealsOnDeath();
    }

    public static double durationMultiplier() {
        return source.durationMultiplier();
    }

    /** Thresholds, ascending; the defaults if the configured list is malformed. */
    public static int[] tierThresholds() {
        int[] t = source.tierThresholds();
        if (t == null || t.length != 5) return Tier.DEFAULT_THRESHOLDS.clone();
        for (int i = 1; i < t.length; i++) {
            if (t[i] < t[i - 1]) return Tier.DEFAULT_THRESHOLDS.clone();
        }
        return t;
    }
}
