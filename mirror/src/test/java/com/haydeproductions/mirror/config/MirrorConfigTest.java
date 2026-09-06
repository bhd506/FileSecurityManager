package com.haydeproductions.mirror.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MirrorConfigTest {
    @TempDir Path temp;

    @Test
    void defaultsAreConservative() {
        MirrorConfig config = MirrorConfig.builder(temp.resolve("source"), temp.resolve("mirror")).build();
        assertEquals(MirrorMode.ONE_TIME, config.mode());
        assertEquals(TransferMode.COPY, config.transferMode());
        assertEquals(ConflictPolicy.FAIL, config.conflictPolicy());
        assertEquals(Duration.ofMillis(150), config.debounce());
        assertEquals(3, config.maxTransferAttempts());
        assertTrue(config.stateFile().isEmpty());
    }

    @Test
    void normalisesPaths() {
        Path source = temp.resolve("x/../source");
        Path mirror = temp.resolve("mirror/./data");
        MirrorConfig config = MirrorConfig.builder(source, mirror).build();
        assertEquals(source.toAbsolutePath().normalize(), config.sourceRoot());
        assertEquals(mirror.toAbsolutePath().normalize(), config.mirrorRoot());
    }

    @Test
    void acceptsCustomOptions() {
        Path state = temp.resolve("state.properties");
        MirrorConfig config = MirrorConfig.builder(temp.resolve("source"), temp.resolve("mirror"))
                .mode(MirrorMode.BIDIRECTIONAL)
                .conflictPolicy(ConflictPolicy.SOURCE_WINS)
                .debounce(Duration.ZERO)
                .maxTransferAttempts(7)
                .stateFile(state)
                .build();
        assertEquals(MirrorMode.BIDIRECTIONAL, config.mode());
        assertEquals(ConflictPolicy.SOURCE_WINS, config.conflictPolicy());
        assertEquals(Duration.ZERO, config.debounce());
        assertEquals(7, config.maxTransferAttempts());
        assertEquals(state.toAbsolutePath().normalize(), config.stateFile().orElseThrow());
    }

    @Test
    void rejectsMoveOutsideOneTimeMode() {
        assertThrows(IllegalArgumentException.class, () -> MirrorConfig
                .builder(temp.resolve("source"), temp.resolve("mirror"))
                .mode(MirrorMode.SOURCE_TO_MIRROR)
                .transferMode(TransferMode.MOVE)
                .build());
    }

    @Test
    void allowsMoveInOneTimeMode() {
        assertDoesNotThrow(() -> MirrorConfig
                .builder(temp.resolve("source"), temp.resolve("mirror"))
                .transferMode(TransferMode.MOVE)
                .build());
    }

    @Test
    void rejectsOverlappingRoots() {
        Path source = temp.resolve("source");
        assertThrows(IllegalArgumentException.class, () -> MirrorConfig.builder(source, source.resolve("mirror")).build());
        assertThrows(IllegalArgumentException.class, () -> MirrorConfig.builder(source.resolve("child"), source).build());
        assertThrows(IllegalArgumentException.class, () -> MirrorConfig.builder(source, source).build());
    }

    @Test
    void rejectsNegativeDebounceAndInvalidAttemptCount() {
        assertThrows(IllegalArgumentException.class, () -> MirrorConfig
                .builder(temp.resolve("source"), temp.resolve("mirror"))
                .debounce(Duration.ofMillis(-1)).build());
        assertThrows(IllegalArgumentException.class, () -> MirrorConfig
                .builder(temp.resolve("source"), temp.resolve("mirror"))
                .maxTransferAttempts(0).build());
    }
}
