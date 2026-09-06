package com.haydeproductions.project.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileStateRegistrySnapshotTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotContainsCurrentNormalizedState() {
        FileStateRegistry registry = new FileStateRegistry();
        Path path = tempDir.resolve("folder/../file.txt");
        Path normalized = tempDir.resolve("file.txt")
                .toAbsolutePath()
                .normalize();

        registry.setState(path, FileState.FLAGGED);

        assertEquals(
                Map.of(normalized, FileState.FLAGGED),
                registry.snapshot()
        );
    }

    @Test
    void snapshotIsIndependentFromLaterRegistryChanges() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        registry.setState(file, FileState.SAFE);
        Map<Path, FileState> snapshot = registry.snapshot();
        registry.setState(file, FileState.FLAGGED);

        assertEquals(
                FileState.SAFE,
                snapshot.get(file.toAbsolutePath().normalize())
        );
    }

    @Test
    void snapshotIsImmutable() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");
        registry.setState(file, FileState.SAFE);

        Map<Path, FileState> snapshot = registry.snapshot();

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.put(
                        tempDir.resolve("other.txt"),
                        FileState.SAFE
                )
        );
    }
}
