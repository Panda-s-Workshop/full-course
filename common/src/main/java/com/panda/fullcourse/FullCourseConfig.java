package com.panda.fullcourse;

import com.panda.fullcourse.logic.Milestones;
import com.panda.fullcourse.logic.Tier;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

        /** Dishes to taste for the third, fourth and fifth meal slots. */
        default int[] slotMilestones() {
            return Milestones.DEFAULT_TARGETS.clone();
        }
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
                           int maxMealSlots, boolean keepMealsOnDeath, double durationMultiplier, int[] tierThresholds,
                           int[] slotMilestones) implements Source {
        /** With the default milestones. */
        public Snapshot(boolean keepVanillaHunger, int recoveryDelaySeconds, int recoveryIntervalTicks, int startingMealSlots,
                        int maxMealSlots, boolean keepMealsOnDeath, double durationMultiplier, int[] tierThresholds) {
            this(keepVanillaHunger, recoveryDelaySeconds, recoveryIntervalTicks, startingMealSlots, maxMealSlots, keepMealsOnDeath,
                    durationMultiplier, tierThresholds, Milestones.DEFAULT_TARGETS.clone());
        }

        public static final StreamCodec<ByteBuf, Snapshot> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Snapshot decode(ByteBuf buf) {
                boolean keep = buf.readBoolean();
                int delay = buf.readInt(), interval = buf.readInt(), start = buf.readInt(), max = buf.readInt();
                boolean keepMeals = buf.readBoolean();
                double multiplier = buf.readDouble();
                int[] thresholds = new int[buf.readUnsignedByte()];
                for (int i = 0; i < thresholds.length; i++) thresholds[i] = buf.readInt();
                int[] milestones = new int[buf.readUnsignedByte()];
                for (int i = 0; i < milestones.length; i++) milestones[i] = buf.readInt();
                return new Snapshot(keep, delay, interval, start, max, keepMeals, multiplier, thresholds, milestones);
            }

            @Override
            public void encode(ByteBuf buf, Snapshot s) {
                buf.writeBoolean(s.keepVanillaHunger()).writeInt(s.recoveryDelaySeconds()).writeInt(s.recoveryIntervalTicks())
                        .writeInt(s.startingMealSlots()).writeInt(s.maxMealSlots()).writeBoolean(s.keepMealsOnDeath())
                        .writeDouble(s.durationMultiplier());
                int[] t = s.tierThresholds();
                buf.writeByte(t.length);
                for (int value : t) buf.writeInt(value);
                int[] m = s.slotMilestones();
                buf.writeByte(m.length);
                for (int value : m) buf.writeInt(value);
            }
        };

        /** The values {@code s} plays by, lists already checked, so a client never reads a list the server rejected. */
        public static Snapshot of(Source s) {
            return new Snapshot(s.keepVanillaHunger(), s.recoveryDelaySeconds(), s.recoveryIntervalTicks(), s.startingMealSlots(),
                    s.maxMealSlots(), s.keepMealsOnDeath(), s.durationMultiplier(), validThresholds(s.tierThresholds()),
                    validMilestones(s.slotMilestones()));
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

    /** On a client, the server's {@code naturalHealthRegeneration} gamerule ({@link com.panda.fullcourse.network.RulesPayload}). */
    private static boolean regenerates = true;

    /** Food heals and Recovery runs: the server's gamerule as last sent; true where none was. */
    public static boolean regenerates() {
        return regenerates;
    }

    public static void useRegeneration(boolean on) {
        regenerates = on;
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
        regenerates = true;
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

    /** Thresholds, strictly ascending; the defaults if the configured list is malformed. */
    public static int[] tierThresholds() {
        return validThresholds(source.tierThresholds());
    }

    /**
     * {@code t} when it holds five strictly ascending scores from 1 to 999, otherwise the defaults: two equal thresholds
     * would leave the tier between them with no foods.
     */
    public static int[] validThresholds(int[] t) {
        boolean valid = t != null && t.length == 5 && t[0] >= 1 && t[4] <= 999;
        for (int i = 1; valid && i < t.length; i++) valid = t[i] > t[i - 1];
        if (valid) return t.clone();
        if (t != null) warnOnce("tierThresholds", Arrays.toString(t), Arrays.toString(Tier.DEFAULT_THRESHOLDS));
        return Tier.DEFAULT_THRESHOLDS.clone();
    }

    /** Dish counts for the third, fourth and fifth meal slots; the defaults if the configured list is malformed. */
    public static int[] slotMilestones() {
        return validMilestones(source.slotMilestones());
    }

    /** {@code m} when it holds three counts from 1 to 9999, the fifth's not below the fourth's, otherwise the defaults. */
    public static int[] validMilestones(int[] m) {
        if (m == null || m.length != 3 || m[0] < 1 || m[1] < 1 || m[2] < m[1] || m[2] > 9999 || m[0] > 9999) {
            if (m != null) warnOnce("slotMilestones", Arrays.toString(m), Arrays.toString(Milestones.DEFAULT_TARGETS));
            return Milestones.DEFAULT_TARGETS.clone();
        }
        return m.clone();
    }

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** Logs once per key and value that a configured value wasn't usable and what is used instead. */
    public static void warnOnce(String key, String value, String using) {
        if (WARNED.add(key + "=" + value)) {
            FullCourse.LOGGER.warn("Full Course: config key {} has {}, which isn't valid; using {}", key, value, using);
        }
    }
}
