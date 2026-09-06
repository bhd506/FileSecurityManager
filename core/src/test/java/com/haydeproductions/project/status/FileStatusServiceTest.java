package com.haydeproductions.project.status;

import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileStatusServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsTrackedStateUsingRelativePath() {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(root.resolve("uploads/file.txt"), FileState.SAFE);

        FileStatusService service = new FileStatusService(root, registry);
        FileStatus status = service.getStatus("uploads/file.txt");

        assertEquals("uploads/file.txt", status.path());
        assertEquals(Optional.of(FileState.SAFE), status.state());
        assertTrue(status.isTracked());
        assertEquals("SAFE", status.getStateName());
        assertTrue(status.isSafe());
    }

    @Test
    void unknownFileReturnsUntrackedApiStatus() {
        FileStatusService service = new FileStatusService(
                tempDir.resolve("data"),
                new FileStateRegistry()
        );

        FileStatus status = service.getStatus("missing.txt");

        assertFalse(status.isTracked());
        assertEquals(Optional.empty(), status.state());
        assertEquals("UNTRACKED", status.getStateName());
        assertFalse(status.isSafe());
    }

    @Test
    void normalizesRelativePathBeforeLookup() {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(root.resolve("file.txt"), FileState.FLAGGED);

        FileStatus status = new FileStatusService(root, registry)
                .getStatus("folder/../file.txt");

        assertEquals("file.txt", status.path());
        assertEquals("FLAGGED", status.getStateName());
    }

    @Test
    void rejectsPathEscapingSourceRoot() {
        FileStatusService service = service();

        assertThrows(
                InvalidStatusPathException.class,
                () -> service.getStatus("../outside.txt")
        );
    }

    @Test
    void rejectsDeepTraversalEscapingSourceRoot() {
        FileStatusService service = service();

        assertThrows(
                InvalidStatusPathException.class,
                () -> service.getStatus("safe/../../outside.txt")
        );
    }

    @Test
    void rejectsAbsolutePath() {
        FileStatusService service = service();

        assertThrows(
                InvalidStatusPathException.class,
                () -> service.getStatus(
                        tempDir.resolve("absolute.txt")
                                .toAbsolutePath()
                                .toString()
                )
        );
    }

    @Test
    void rejectsBlankSingleFilePath() {
        assertThrows(
                InvalidStatusPathException.class,
                () -> service().getStatus("  ")
        );
    }

    @Test
    void rejectsSourceRootAsSingleFilePath() {
        assertThrows(
                InvalidStatusPathException.class,
                () -> service().getStatus(".")
        );
    }

    @Test
    void listReturnsOnlyStatesInsideSourceRoot() {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();

        registry.setState(root.resolve("a.txt"), FileState.SAFE);
        registry.setState(root.resolve("nested/b.txt"), FileState.FLAGGED);
        registry.setState(tempDir.resolve("outside.txt"), FileState.ERROR);

        List<FileStatus> statuses =
                new FileStatusService(root, registry).listStatuses();

        assertEquals(
                List.of("a.txt", "nested/b.txt"),
                statuses.stream().map(FileStatus::path).toList()
        );
    }

    @Test
    void listIsSortedByPortableRelativePath() {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();

        registry.setState(root.resolve("z.txt"), FileState.SAFE);
        registry.setState(root.resolve("a/b.txt"), FileState.FLAGGED);
        registry.setState(root.resolve("a/a.txt"), FileState.ERROR);

        List<FileStatus> statuses =
                new FileStatusService(root, registry).listStatuses();

        assertEquals(
                List.of("a/a.txt", "a/b.txt", "z.txt"),
                statuses.stream().map(FileStatus::path).toList()
        );
    }

    @Test
    void listByPrefixUsesPathSegmentsRatherThanStringPrefix() {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();

        registry.setState(root.resolve("uploads/a.txt"), FileState.SAFE);
        registry.setState(root.resolve("uploads/nested/b.txt"), FileState.FLAGGED);
        registry.setState(root.resolve("uploads-other/c.txt"), FileState.ERROR);

        List<FileStatus> statuses =
                new FileStatusService(root, registry)
                        .listStatuses("uploads");

        assertEquals(
                List.of("uploads/a.txt", "uploads/nested/b.txt"),
                statuses.stream().map(FileStatus::path).toList()
        );
    }

    @Test
    void dotPrefixListsEntireSourceRoot() {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(root.resolve("file.txt"), FileState.SAFE);

        assertEquals(
                1,
                new FileStatusService(root, registry)
                        .listStatuses(".")
                        .size()
        );
    }

    @Test
    void prefixCannotEscapeSourceRoot() {
        assertThrows(
                InvalidStatusPathException.class,
                () -> service().listStatuses("../outside")
        );
    }

    @Test
    void returnedListIsImmutable() {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(root.resolve("file.txt"), FileState.SAFE);

        List<FileStatus> statuses =
                new FileStatusService(root, registry).listStatuses();

        assertThrows(
                UnsupportedOperationException.class,
                () -> statuses.add(FileStatus.untracked("x.txt"))
        );
    }

    @Test
    void constructorNormalizesSourceRoot() {
        Path root = tempDir.resolve("folder/../data");

        FileStatusService service = new FileStatusService(
                root,
                new FileStateRegistry()
        );

        assertEquals(
                tempDir.resolve("data").toAbsolutePath().normalize(),
                service.getSourceRoot()
        );
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(
                NullPointerException.class,
                () -> new FileStatusService(null, new FileStateRegistry())
        );

        assertThrows(
                NullPointerException.class,
                () -> new FileStatusService(tempDir, null)
        );
    }

    private FileStatusService service() {
        return new FileStatusService(
                tempDir.resolve("data"),
                new FileStateRegistry()
        );
    }
}
