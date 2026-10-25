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
    void readsBothKeys() {
        assertEquals(new ClientOptions.Values(24, true, false),
                ClientOptions.fromProperties(props("mealRowOffset", " 24 ", "showRecoveryParticles", "TRUE", "showTierPips", "false")));
    }

    @Test
    void clampsTheOffsetAndIgnoresBadValues() {
        assertEquals(ClientOptions.MAX_OFFSET, ClientOptions.fromProperties(props("mealRowOffset", "9999")).mealRowOffset());
        assertEquals(0, ClientOptions.fromProperties(props("mealRowOffset", "-5")).mealRowOffset());
        assertEquals(ClientOptions.DEFAULTS, ClientOptions.fromProperties(props("mealRowOffset", "lots", "showRecoveryParticles", "yes", "showTierPips", "no")));
    }

    @Test
    void theOffsetInUseIsClampedWhateverTheLoaderGives() {
        try {
            ClientOptions.use(new ClientOptions.Values(500, false, true));
            assertEquals(ClientOptions.MAX_OFFSET, ClientOptions.mealRowOffset());
        } finally {
            ClientOptions.use(ClientOptions.DEFAULTS);
        }
    }
}
