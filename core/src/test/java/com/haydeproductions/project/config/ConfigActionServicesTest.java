package com.haydeproductions.project.config;

import com.haydeproductions.project.log.NoOpLogHandler;
import com.haydeproductions.project.quarantine.QuarantineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigActionServicesTest {

    @TempDir
    Path tempDir;

    @Test
    void noneContainsNoServices() {
        ConfigActionServices services = ConfigActionServices.none();
        assertTrue(services.getLogHandler().isEmpty());
        assertTrue(services.getQuarantineService().isEmpty());
    }

    @Test
    void withLogHandlerContainsOnlyLogHandler() {
        ConfigActionServices services =
                ConfigActionServices.withLogHandler(
                        NoOpLogHandler.INSTANCE
                );

        assertEquals(
                NoOpLogHandler.INSTANCE,
                services.getLogHandler().orElseThrow()
        );
        assertTrue(services.getQuarantineService().isEmpty());
    }

    @Test
    void ofContainsBothServices() throws Exception {
        QuarantineService quarantine =
                new QuarantineService(tempDir.resolve("q"));

        ConfigActionServices services = ConfigActionServices.of(
                NoOpLogHandler.INSTANCE,
                quarantine
        );

        assertEquals(
                NoOpLogHandler.INSTANCE,
                services.getLogHandler().orElseThrow()
        );
        assertEquals(
                quarantine,
                services.getQuarantineService().orElseThrow()
        );
    }

    @Test
    void factoriesRejectNullDependencies() {
        assertThrows(
                NullPointerException.class,
                () -> ConfigActionServices.withLogHandler(null)
        );
        assertThrows(
                NullPointerException.class,
                () -> ConfigActionServices.of(
                        null,
                        new QuarantineService(tempDir.resolve("q1"))
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> ConfigActionServices.of(
                        NoOpLogHandler.INSTANCE,
                        null
                )
        );
    }
}
