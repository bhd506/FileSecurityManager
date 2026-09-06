package com.haydeproductions.project.mirror;

import com.haydeproductions.mirror.config.ConflictPolicy;
import com.haydeproductions.mirror.config.MirrorMode;
import com.haydeproductions.mirror.config.TransferMode;
import com.haydeproductions.project.config.MirrorDefinition;
import com.haydeproductions.project.log.NoOpLogHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MirrorManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void allowAndDenyControlActiveTargets() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("source"));
        Path file = sourceRoot.resolve("file.txt");
        Files.writeString(file, "v1");

        try (MirrorManager manager = manager(sourceRoot, definition("backup", "backup"))) {
            manager.allow(file, List.of("backup"));
            assertEquals(Set.of("backup"), manager.activeMirrorIds(file));
            assertEquals(
                    "v1",
                    Files.readString(tempDir.resolve("mirrors/backup/file.txt"))
            );

            manager.deny(file, List.of("backup"));
            assertTrue(manager.activeMirrorIds(file).isEmpty());
        }
    }

    @Test
    void sourceModificationDoesNotBypassSecurityReauthorization() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("source-gated"));
        Path file = sourceRoot.resolve("file.txt");
        Files.writeString(file, "approved-v1");

        try (MirrorManager manager = manager(
                sourceRoot,
                new MirrorDefinition(
                        "backup",
                        Path.of("gated"),
                        MirrorMode.SOURCE_TO_MIRROR,
                        TransferMode.COPY,
                        ConflictPolicy.FAIL,
                        Duration.ofMillis(25),
                        3,
                        false
                )
        )) {
            manager.start();
            manager.allow(file, List.of("backup"));
            Path mirror = tempDir.resolve("mirrors/gated/file.txt");
            assertEquals("approved-v1", Files.readString(mirror));

            Files.writeString(file, "unscanned-v2");
            Thread.sleep(250);
            assertEquals("approved-v1", Files.readString(mirror));

            manager.revokeAll(file);
            manager.allow(file, List.of("backup"));
            assertEquals("unscanned-v2", Files.readString(mirror));
        }
    }

    @Test
    void overlappingConfiguredMirrorDestinationsAreRejected() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("source-overlap"));
        List<MirrorDefinition> definitions = List.of(
                definition("one", "a"),
                definition("two", "a/nested")
        );

        assertThrows(
                java.io.IOException.class,
                () -> MirrorManager.create(
                        sourceRoot,
                        tempDir.resolve("mirrors-overlap"),
                        tempDir.resolve("states-overlap"),
                        definitions,
                        NoOpLogHandler.INSTANCE
                )
        );
    }

    private MirrorManager manager(
            Path sourceRoot,
            MirrorDefinition... definitions
    ) throws Exception {
        return MirrorManager.create(
                sourceRoot,
                tempDir.resolve("mirrors"),
                tempDir.resolve("states"),
                List.of(definitions),
                NoOpLogHandler.INSTANCE
        );
    }

    private MirrorDefinition definition(String id, String path) {
        return new MirrorDefinition(
                id,
                Path.of(path),
                MirrorMode.ONE_TIME,
                TransferMode.COPY,
                ConflictPolicy.FAIL,
                Duration.ofMillis(25),
                3,
                false
        );
    }
}
