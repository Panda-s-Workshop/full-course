package com.panda.fullcourse.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/** The client settings file: defaults, a full file, clamping and values that don't parse. */
class ClientOptionsTest {
    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    @Test
    void anEmptyFileGivesTheDefaults() {
        assertEquals(ClientOptions.DEFAULTS, ClientOptions.fromProperties(new Properties()));
        assertEquals(ClientOptions.DEFAULTS, ClientOptions.fromProperties(ClientOptions.defaults()));
    }

    @Test
    void readsEveryKey() {
        assertEquals(new ClientOptions.Values(ClientOptions.Anchor.TOP_RIGHT, -12, 30, ClientOptions.ListSide.RIGHT, 5, -8, true, false),
                ClientOptions.fromProperties(props("mealRowAnchor", " Top_Right ", "mealRowX", "-12", "mealRowY", " 30 ",
                        "mealList", "right", "mealListX", "5", "mealListY", "-8", "showRecoveryParticles", "TRUE", "showTierPips", "false")));
        assertEquals(ClientOptions.Anchor.BOTTOM_LEFT, ClientOptions.fromProperties(props("mealRowAnchor", "bottom-left")).mealRowAnchor());
        assertEquals(ClientOptions.ListSide.OFF, ClientOptions.fromProperties(props("mealList", "OFF")).mealList());
    }

    @Test
    void clampsTheShiftsAndIgnoresBadValues() {
        assertEquals(ClientOptions.MAX_SHIFT, ClientOptions.fromProperties(props("mealRowX", "99999")).mealRowX());
        assertEquals(-ClientOptions.MAX_SHIFT, ClientOptions.fromProperties(props("mealListY", "-99999")).mealListY());
        assertEquals(ClientOptions.DEFAULTS, ClientOptions.fromProperties(props("mealRowAnchor", "middle", "mealRowY", "lots",
                "mealList", "under", "showRecoveryParticles", "yes", "showTierPips", "no")));
    }

    @Test
    void theShiftsInUseAreClampedWhateverTheLoaderGives() {
        try {
            ClientOptions.use(new ClientOptions.Values(null, 5000, -5000, null, 5000, 0, false, true));
            assertEquals(ClientOptions.MAX_SHIFT, ClientOptions.mealRowX());
            assertEquals(-ClientOptions.MAX_SHIFT, ClientOptions.mealRowY());
            assertEquals(ClientOptions.MAX_SHIFT, ClientOptions.mealListX());
            assertEquals(ClientOptions.Anchor.HOTBAR, ClientOptions.mealRowAnchor());
            assertEquals(ClientOptions.ListSide.LEFT, ClientOptions.mealList());
        } finally {
            ClientOptions.use(ClientOptions.DEFAULTS);
        }
    }

    @Test
    void theDefaultFileNamesEveryKeyInLowerCase() {
        Properties p = ClientOptions.defaults();
        assertEquals("hotbar", p.getProperty("mealRowAnchor"));
        assertEquals("left", p.getProperty("mealList"));
        assertEquals(8, p.size());
    }
}
