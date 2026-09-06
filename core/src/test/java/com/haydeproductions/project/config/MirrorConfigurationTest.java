package com.haydeproductions.project.config;

import com.haydeproductions.mirror.config.ConflictPolicy;
import com.haydeproductions.mirror.config.MirrorMode;
import com.haydeproductions.mirror.config.TransferMode;
import com.haydeproductions.project.rule.action.AllowMirrorAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MirrorConfigurationTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesMirrorDefinitionsAndStructuredMirrorActions() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("source"));
        Path config = write("""
                version: 1
                runtime:
                  debounce: 75ms
                  workerThreads: 2
                mirrors:
                  - id: backup
                    path: backup-a
                    mode: sourceToMirror
                    transferMode: copy
                    conflictPolicy: sourceWins
                    debounce: 40ms
                    maxTransferAttempts: 5
                    persistentState: true
                ruleSets:
                  - path: "."
                    rules:
                      - custom:
                          condition: true
                          onMatch:
                            - allowMirror: backup
                """);

        Config loaded = ConfigLoader.load(config, root);

        assertEquals(Duration.ofMillis(75), loaded.getRuntime().debounce());
        assertEquals(2, loaded.getRuntime().workerThreads());
        assertEquals(1, loaded.getMirrors().size());

        MirrorDefinition mirror = loaded.getMirrors().getFirst();
        assertEquals("backup", mirror.id());
        assertEquals(Path.of("backup-a"), mirror.path());
        assertEquals(MirrorMode.SOURCE_TO_MIRROR, mirror.mode());
        assertEquals(TransferMode.COPY, mirror.transferMode());
        assertEquals(ConflictPolicy.SOURCE_WINS, mirror.conflictPolicy());
        assertEquals(Duration.ofMillis(40), mirror.debounce());
        assertEquals(5, mirror.maxTransferAttempts());
        assertTrue(mirror.persistentState());

        assertInstanceOf(
                AllowMirrorAction.class,
                loaded.getRuleSets().getFirst()
                        .getRules().getFirst()
                        .getOnMatch().getFirst()
        );
    }

    @Test
    void unknownMirrorIdInActionIsRejected() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("source-unknown"));
        Path config = write("""
                mirrors:
                  - id: backup
                    path: backup
                ruleSets:
                  - path: "."
                    rules:
                      - custom:
                          condition: true
                          onMatch:
                            - allowMirror: missing
                """);

        assertThrows(
                ConfigException.class,
                () -> ConfigLoader.load(config, root)
        );
    }

    @Test
    void mirrorIdsMustBeUniqueIgnoringCase() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("source-dupes"));
        Path config = write("""
                mirrors:
                  - id: Backup
                    path: one
                  - id: backup
                    path: two
                """);

        assertThrows(
                ConfigException.class,
                () -> ConfigLoader.load(config, root)
        );
    }

    @Test
    void bidirectionalMirrorsPersistStateByDefault() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("source-persist"));
        Path config = write("""
                mirrors:
                  - id: sync
                    path: sync
                    mode: bidirectional
                """);

        Config loaded = ConfigLoader.load(config, root);
        assertTrue(loaded.getMirrors().getFirst().persistentState());
    }

    private Path write(String yaml) throws Exception {
        Path file = tempDir.resolve("config-" + System.nanoTime() + ".yaml");
        Files.writeString(file, yaml);
        return file;
    }
}
