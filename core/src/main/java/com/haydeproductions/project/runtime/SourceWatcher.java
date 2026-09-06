package com.haydeproductions.project.runtime;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

public final class SourceWatcher implements AutoCloseable {

    private final Path root;
    private final SourceWatchListener listener;
    private final Map<WatchKey, Path> directoriesByKey =
            new ConcurrentHashMap<>();
    private final Set<Path> watchedDirectories =
            ConcurrentHashMap.newKeySet();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile WatchService watchService;
    private volatile Thread watchThread;

    public SourceWatcher(
            Path root,
            SourceWatchListener listener
    ) {
        this.root = normalize(root);
        this.listener = Objects.requireNonNull(listener);
    }

    public Path getRoot() {
        return root;
    }

    public synchronized void initialize() throws IOException {
        ensureOpen();

        if (initialized.get()) {
            return;
        }

        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(
                    "Source root does not exist or is not a directory: " + root
            );
        }

        WatchService created = root.getFileSystem().newWatchService();
        watchService = created;

        try {
            registerRecursively(root);
            initialized.set(true);
        } catch (IOException exception) {
            try {
                created.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            watchService = null;
            throw exception;
        }
    }

    public synchronized void start() throws IOException {
        ensureOpen();

        if (!initialized.get()) {
            initialize();
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        watchThread = new Thread(
                this::watchLoop,
                "source-watch-service"
        );
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getWatchedDirectoryCount() {
        return watchedDirectories.size();
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        running.set(false);

        WatchService service = watchService;
        watchService = null;

        if (service != null) {
            try {
                service.close();
            } catch (IOException exception) {
                listener.onWatcherError(exception);
            }
        }

        Thread thread = watchThread;
        watchThread = null;

        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(5_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        for (WatchKey key : directoriesByKey.keySet()) {
            key.cancel();
        }

        directoriesByKey.clear();
        watchedDirectories.clear();
    }

    private void watchLoop() {
        while (running.get()) {
            WatchKey key;

            try {
                WatchService service = watchService;
                if (service == null) {
                    return;
                }

                key = service.take();

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;

            } catch (ClosedWatchServiceException exception) {
                return;

            } catch (RuntimeException exception) {
                listener.onWatcherError(exception);
                return;
            }

            Path directory = directoriesByKey.get(key);

            if (directory == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> rawEvent : key.pollEvents()) {
                WatchEvent.Kind<?> kind = rawEvent.kind();

                if (kind == OVERFLOW) {
                    listener.onOverflow(directory);
                    continue;
                }

                Object context = rawEvent.context();
                if (!(context instanceof Path relativePath)) {
                    continue;
                }

                Path child = directory.resolve(relativePath)
                        .toAbsolutePath()
                        .normalize();

                try {
                    if (kind == ENTRY_CREATE) {
                        handleCreate(child);
                    } else if (kind == ENTRY_MODIFY) {
                        handleModify(child);
                    } else if (kind == ENTRY_DELETE) {
                        handleDelete(child);
                    }
                } catch (IOException exception) {
                    listener.onWatcherError(exception);
                }
            }

            boolean valid = key.reset();

            if (!valid) {
                Path invalidDirectory = directoriesByKey.remove(key);
                if (invalidDirectory != null) {
                    watchedDirectories.remove(invalidDirectory);

                    if (invalidDirectory.equals(root) && running.get()) {
                        listener.onWatcherError(
                                new IOException(
                                        "Watch registration for source root became invalid: "
                                                + root
                                )
                        );
                    }
                }
            }
        }
    }

    private void handleCreate(Path child) throws IOException {
        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            registerRecursively(child);
            listener.onDirectoryCreated(child);
            return;
        }

        if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
            listener.onFileChanged(child);
        }
    }

    private void handleModify(Path child) {
        if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
            listener.onFileChanged(child);
        }
    }

    private void handleDelete(Path child) {
        if (watchedDirectories.contains(child)) {
            unregisterSubtree(child);
            listener.onDirectoryDeleted(child);
        } else {
            listener.onFileDeleted(child);
        }
    }

    private void registerRecursively(Path start) throws IOException {
        Files.walkFileTree(
                start,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory,
                            BasicFileAttributes attributes
                    ) throws IOException {
                        registerDirectory(directory);
                        return FileVisitResult.CONTINUE;
                    }
                }
        );
    }

    private void registerDirectory(Path directory) throws IOException {
        Path normalizedDirectory = normalize(directory);

        if (!watchedDirectories.add(normalizedDirectory)) {
            return;
        }

        WatchService service = watchService;
        if (service == null) {
            watchedDirectories.remove(normalizedDirectory);
            throw new ClosedWatchServiceException();
        }

        try {
            WatchKey key = normalizedDirectory.register(
                    service,
                    ENTRY_CREATE,
                    ENTRY_MODIFY,
                    ENTRY_DELETE
            );

            directoriesByKey.put(key, normalizedDirectory);

        } catch (IOException | RuntimeException exception) {
            watchedDirectories.remove(normalizedDirectory);
            throw exception;
        }
    }

    private void unregisterSubtree(Path subtree) {
        Path normalizedSubtree = normalize(subtree);

        for (Map.Entry<WatchKey, Path> entry : directoriesByKey.entrySet()) {
            if (entry.getValue().startsWith(normalizedSubtree)) {
                WatchKey key = entry.getKey();
                key.cancel();
                directoriesByKey.remove(key, entry.getValue());
                watchedDirectories.remove(entry.getValue());
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("SourceWatcher is closed");
        }
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }
}
