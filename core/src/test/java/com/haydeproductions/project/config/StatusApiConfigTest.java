package com.haydeproductions.project.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusApiConfigTest {

    @Test
    void disabledUsesSafeDefaults() {
        StatusApiConfig config = StatusApiConfig.disabled();

        assertFalse(config.enabled());
        assertEquals("127.0.0.1", config.host());
        assertEquals(8080, config.port());
    }

    @Test
    void acceptsEphemeralPortZero() {
        StatusApiConfig config = new StatusApiConfig(
                true,
                "127.0.0.1",
                0
        );

        assertEquals(0, config.port());
    }

    @Test
    void rejectsBlankHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StatusApiConfig(true, " ", 8080)
        );
    }

    @Test
    void rejectsOutOfRangePorts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StatusApiConfig(true, "127.0.0.1", -1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new StatusApiConfig(true, "127.0.0.1", 65_536)
        );
    }
}
