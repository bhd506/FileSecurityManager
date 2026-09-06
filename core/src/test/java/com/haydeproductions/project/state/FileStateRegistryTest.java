package com.haydeproductions.project.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileStateRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndReturnsState() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        registry.setState(file, FileState.SCANNING);

        assertEquals(
                Optional.of(FileState.SCANNING),
                registry.getState(file)
        );
    }

    @Test
    void unknownPathHasNoState() {
        FileStateRegistry registry = new FileStateRegistry();

        assertEquals(
                Optional.empty(),
                registry.getState(tempDir.resolve("missing.txt"))
        );
    }

    @Test
    void settingStateReplacesPreviousState() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        registry.setState(file, FileState.UNSCANNED);
        registry.setState(file, FileState.SCANNING);
        registry.setState(file, FileState.FLAGGED);

        assertEquals(
                Optional.of(FileState.FLAGGED),
                registry.getState(file)
        );
    }

    @Test
    void pathsAreNormalizedForStorageAndLookup() {
        FileStateRegistry registry = new FileStateRegistry();

        Path storedPath = tempDir
                .resolve("folder")
                .resolve("..")
                .resolve("file.txt");

        Path lookupPath = tempDir.resolve("file.txt");

        registry.setState(storedPath, FileState.SAFE);

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(lookupPath)
        );
    }

    @Test
    void relativeAndAbsoluteEquivalentPathsUseSameEntry() {
        FileStateRegistry registry = new FileStateRegistry();

        Path absolute = tempDir
                .resolve("file.txt")
                .toAbsolutePath()
                .normalize();

        Path relativeEquivalent = Path.of("")
                .toAbsolutePath()
                .normalize()
                .relativize(absolute);

        /*
         * Only run the equivalence assertion when relativize produced a
         * usable relative path on this filesystem/root combination.
         */
        registry.setState(relativeEquivalent, FileState.FLAGGED);

        assertEquals(
                Optional.of(FileState.FLAGGED),
                registry.getState(absolute)
        );
    }

    @Test
    void removeDeletesStateAndReturnsPreviousState() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        registry.setState(file, FileState.QUARANTINED);

        assertEquals(
                Optional.of(FileState.QUARANTINED),
                registry.remove(file)
        );

        assertEquals(
                Optional.empty(),
                registry.getState(file)
        );
    }

    @Test
    void removingUnknownPathReturnsEmpty() {
        FileStateRegistry registry = new FileStateRegistry();

        assertEquals(
                Optional.empty(),
                registry.remove(tempDir.resolve("missing.txt"))
        );
    }

    @Test
    void removeUsesNormalizedPath() {
        FileStateRegistry registry = new FileStateRegistry();

        Path storedPath = tempDir.resolve("file.txt");
        Path removalPath = tempDir
                .resolve("folder")
                .resolve("..")
                .resolve("file.txt");

        registry.setState(storedPath, FileState.SAFE);

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.remove(removalPath)
        );

        assertEquals(
                Optional.empty(),
                registry.getState(storedPath)
        );
    }

    @Test
    void containsReturnsTrueForTrackedPath() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        registry.setState(file, FileState.UNSCANNED);

        assertTrue(registry.contains(file));
    }

    @Test
    void containsReturnsFalseForUntrackedPath() {
        FileStateRegistry registry = new FileStateRegistry();

        assertFalse(
                registry.contains(tempDir.resolve("file.txt"))
        );
    }

    @Test
    void containsUsesNormalizedPath() {
        FileStateRegistry registry = new FileStateRegistry();

        Path storedPath = tempDir.resolve("file.txt");
        Path lookupPath = tempDir
                .resolve("folder")
                .resolve("..")
                .resolve("file.txt");

        registry.setState(storedPath, FileState.SAFE);

        assertTrue(registry.contains(lookupPath));
    }

    @Test
    void compareAndSetChangesMatchingState() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        registry.setState(file, FileState.SCANNING);

        assertTrue(
                registry.compareAndSet(
                        file,
                        FileState.SCANNING,
                        FileState.SAFE
                )
        );

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(file)
        );
    }

    @Test
    void compareAndSetDoesNotChangeDifferentState() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        registry.setState(file, FileState.FLAGGED);

        assertFalse(
                registry.compareAndSet(
                        file,
                        FileState.SCANNING,
                        FileState.SAFE
                )
        );

        assertEquals(
                Optional.of(FileState.FLAGGED),
                registry.getState(file)
        );
    }

    @Test
    void compareAndSetReturnsFalseForUntrackedPath() {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        assertFalse(
                registry.compareAndSet(
                        file,
                        FileState.SCANNING,
                        FileState.SAFE
                )
        );

        assertEquals(
                Optional.empty(),
                registry.getState(file)
        );
    }

    @Test
    void compareAndSetUsesNormalizedPath() {
        FileStateRegistry registry = new FileStateRegistry();

        Path storedPath = tempDir.resolve("file.txt");
        Path updatePath = tempDir
                .resolve("folder")
                .resolve("..")
                .resolve("file.txt");

        registry.setState(storedPath, FileState.SCANNING);

        assertTrue(
                registry.compareAndSet(
                        updatePath,
                        FileState.SCANNING,
                        FileState.SAFE
                )
        );

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(storedPath)
        );
    }

    @Test
    void setStateRejectsNullPath() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> registry.setState(null, FileState.SAFE)
        );
    }

    @Test
    void setStateRejectsNullState() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> registry.setState(
                        tempDir.resolve("file.txt"),
                        null
                )
        );
    }

    @Test
    void getStateRejectsNullPath() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> registry.getState(null)
        );
    }

    @Test
    void removeRejectsNullPath() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> registry.remove(null)
        );
    }

    @Test
    void containsRejectsNullPath() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> registry.contains(null)
        );
    }

    @Test
    void compareAndSetRejectsNullPath() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> registry.compareAndSet(
                        null,
                        FileState.SCANNING,
                        FileState.SAFE
                )
        );
    }

    @Test
    void compareAndSetRejectsNullExpectedState() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> registry.compareAndSet(
                        tempDir.resolve("file.txt"),
                        null,
                        FileState.SAFE
                )
        );
    }

    @Test
    void compareAndSetRejectsNullNewState() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> registry.compareAndSet(
                        tempDir.resolve("file.txt"),
                        FileState.SCANNING,
                        null
                )
        );
    }
}
