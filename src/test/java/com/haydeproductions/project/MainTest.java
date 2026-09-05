package com.haydeproductions.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void suppliedConfigPathIsAccepted() throws IOException {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, "{}\n");

        assertDoesNotThrow(() -> Main.main(new String[]{config.toString()}));
    }

    @Test
    void missingSuppliedConfigPathPropagatesIOException() {
        Path missing = tempDir.resolve("missing.yaml");

        assertThrows(
                IOException.class,
                () -> Main.main(new String[]{missing.toString()})
        );
    }
}
