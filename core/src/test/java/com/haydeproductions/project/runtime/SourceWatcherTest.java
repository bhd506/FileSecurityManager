package com.haydeproductions.project.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SourceWatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeRegistersExistingDirectoryTree() throws Exception {
        Files.createDirectories(tempDir.resolve("one/two/three"));

        try (SourceWatcher watcher = new SourceWatcher(
                tempDir,
                new SourceWatchListener() { }
        )) {
            watcher.initialize();

            assertTrue(watcher.isInitialized());
            assertEquals(4, watcher.getWatchedDirectoryCount());
        }
    }

    @Test
    void eventCreatedAfterInitializeButBeforeStartIsStillDelivered()
            throws Exception {

        CountDownLatch changed = new CountDownLatch(1);
        AtomicReference<Path> received = new AtomicReference<>();

        try (SourceWatcher watcher = new SourceWatcher(
                tempDir,
                new SourceWatchListener() {
                    @Override
                    public void onFileChanged(Path path) {
                        received.set(path);
                        changed.countDown();
                    }
                }
        )) {
            watcher.initialize();

            Path file = tempDir.resolve("between.txt");
            Files.writeString(file, "data");

            watcher.start();

            assertTrue(changed.await(5, TimeUnit.SECONDS));
            assertEquals(
                    file.toAbsolutePath().normalize(),
                    received.get()
            );
        }
    }

    @Test
    void detectsFileDeletion() throws Exception {
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);
        AtomicReference<Path> deletedPath = new AtomicReference<>();

        try (SourceWatcher watcher = new SourceWatcher(
                tempDir,
                new SourceWatchListener() {
                    @Override
                    public void onFileChanged(Path path) {
                        changed.countDown();
                    }

                    @Override
                    public void onFileDeleted(Path path) {
                        deletedPath.set(path);
                        deleted.countDown();
                    }
                }
        )) {
            watcher.start();

            Path file = tempDir.resolve("delete-me.txt");
            Files.writeString(file, "data");
            assertTrue(changed.await(5, TimeUnit.SECONDS));

            Files.delete(file);

            assertTrue(deleted.await(5, TimeUnit.SECONDS));
            assertEquals(
                    file.toAbsolutePath().normalize(),
                    deletedPath.get()
            );
        }
    }

    @Test
    void dynamicallyRegistersNewDirectoryTrees() throws Exception {
        CountDownLatch directorySeen = new CountDownLatch(1);
        CountDownLatch fileSeen = new CountDownLatch(1);
        AtomicReference<Path> createdDirectory = new AtomicReference<>();
        AtomicReference<Path> changedFile = new AtomicReference<>();

        try (SourceWatcher watcher = new SourceWatcher(
                tempDir,
                new SourceWatchListener() {
                    @Override
                    public void onDirectoryCreated(Path path) {
                        createdDirectory.set(path);
                        directorySeen.countDown();
                    }

                    @Override
                    public void onFileChanged(Path path) {
                        changedFile.set(path);
                        fileSeen.countDown();
                    }
                }
        )) {
            watcher.start();

            Path directory = tempDir.resolve("new-tree");
            Files.createDirectories(directory.resolve("nested"));

            assertTrue(directorySeen.await(5, TimeUnit.SECONDS));

            Path file = directory.resolve("nested/file.txt");
            Files.writeString(file, "data");

            assertTrue(fileSeen.await(5, TimeUnit.SECONDS));
            assertTrue(
                    changedFile.get().startsWith(
                            directory.toAbsolutePath().normalize()
                    )
            );
            assertEquals(
                    directory.toAbsolutePath().normalize(),
                    createdDirectory.get()
            );
        }
    }

    @Test
    void missingRootIsRejectedDuringInitialization() {
        Path missing = tempDir.resolve("missing");

        try (SourceWatcher watcher = new SourceWatcher(
                missing,
                new SourceWatchListener() { }
        )) {
            assertThrows(
                    NoSuchFileException.class,
                    watcher::initialize
            );
        }
    }

    @Test
    void closeIsIdempotent() throws Exception {
        SourceWatcher watcher = new SourceWatcher(
                tempDir,
                new SourceWatchListener() { }
        );

        watcher.start();
        watcher.close();
        watcher.close();

        assertFalse(watcher.isRunning());
    }
}
