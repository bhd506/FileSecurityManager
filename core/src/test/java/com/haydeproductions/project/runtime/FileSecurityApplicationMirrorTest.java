package com.haydeproductions.project.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FileSecurityApplicationMirrorTest {
    @TempDir
    Path tempDir;

    @Test
    void configCanAuthorizeAndContinuouslyReauthorizeMirroring() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("source"));
        Path quarantine = tempDir.resolve("quarantine");
        Path logs = tempDir.resolve("logs");
        Path mirrors = tempDir.resolve("mirrors");
        Path file = source.resolve("file.txt");
        Files.writeString(file, "v1");

        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
                version: 1
                runtime:
                  debounce: 25ms
                  workerThreads: 2
                mirrors:
                  - id: backup
                    path: backup
                    mode: sourceToMirror
                    debounce: 25ms
                ruleSets:
                  - path: "."
                    rules:
                      - custom:
                          condition: true
                          onMatch:
                            - allowMirror: backup
                """);

        try (FileSecurityApplication application =
                     FileSecurityApplication.create(
                             config,
                             source,
                             quarantine,
                             logs,
                             mirrors
                     )) {
            application.start();

            Path mirrored = mirrors.resolve("backup/file.txt");
            assertEquals("v1", Files.readString(mirrored));
            assertEquals(
                    java.util.Set.of("backup"),
                    application.getMirrorManager().activeMirrorIds(file)
            );

            Files.writeString(file, "v2");
            awaitContent(mirrored, "v2", Duration.ofSeconds(5));
            assertEquals(
                    java.util.Set.of("backup"),
                    application.getMirrorManager().activeMirrorIds(file)
            );
        }
    }

    @Test
    void applicationRejectsOperationalRootsThatOverlapSource() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("source-overlap"));
        Path config = tempDir.resolve("config-overlap.yaml");
        Files.writeString(config, "{}\n");

        assertThrows(
                java.io.IOException.class,
                () -> FileSecurityApplication.create(
                        config,
                        source,
                        source.resolve("quarantine"),
                        tempDir.resolve("logs-overlap"),
                        tempDir.resolve("mirrors-overlap")
                )
        );
    }

    private void awaitContent(Path file, String expected, Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(file) && expected.equals(Files.readString(file))) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for mirror content: " + expected);
    }
}
