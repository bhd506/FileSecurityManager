package com.haydeproductions.project.runtime;

import com.haydeproductions.project.log.LogEntry;
import com.haydeproductions.project.log.LogEventType;
import com.haydeproductions.project.log.LogHandler;
import com.haydeproductions.project.scan.FileChangedDuringScanException;
import com.haydeproductions.project.scan.FileScanner;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class FileSecurityRuntime implements AutoCloseable {

    public static final Duration DEFAULT_DEBOUNCE =
            Duration.ofMillis(150);

    private final Path sourceRoot;
    private final FileScanner fileScanner;
    private final FileStateRegistry stateRegistry;
    private final LogHandler logHandler;
    private final FileScanScheduler scanScheduler;
    private final SourceWatcher sourceWatcher;
    private final ExecutorService reconciliationExecutor;
    private final AtomicInteger reconciliationTasks = new AtomicInteger();
    private final AtomicBoolean fullRescanPending = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public FileSecurityRuntime(
            Path sourceRoot,
            FileScanner fileScanner,
            FileStateRegistry stateRegistry,
            LogHandler logHandler
    ) {
        this(
                sourceRoot,
                fileScanner,
                stateRegistry,
                logHandler,
                DEFAULT_DEBOUNCE,
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
    }

    public FileSecurityRuntime(
            Path sourceRoot,
            FileScanner fileScanner,
            FileStateRegistry stateRegistry,
            LogHandler logHandler,
            Duration debounce,
            int workerThreads
    ) {
        this.sourceRoot = normalize(sourceRoot);
        this.fileScanner = Objects.requireNonNull(fileScanner);
        this.stateRegistry = Objects.requireNonNull(stateRegistry);
        this.logHandler = Objects.requireNonNull(logHandler);

        this.scanScheduler = new FileScanScheduler(
                fileScanner::scan,
                Objects.requireNonNull(debounce),
                workerThreads,
                this::handleScanFailure
        );

        this.reconciliationExecutor =
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "source-reconciliation"
                    );
                    thread.setDaemon(true);
                    return thread;
                });

        this.sourceWatcher = new SourceWatcher(
                this.sourceRoot,
                new RuntimeWatchListener()
        );
    }

    public Path getSourceRoot() {
        return sourceRoot;
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start() throws IOException {
        ensureOpen();

        if (!started.compareAndSet(false, true)) {
            if (running.get()) {
                return;
            }
            throw new IllegalStateException(
                    "FileSecurityRuntime cannot be restarted after it has stopped"
            );
        }

        try {
            /*
             * Register the tree before the initial scan. Filesystem events
             * that occur during that scan accumulate in WatchService and are
             * processed after the baseline scan, eliminating the usual
             * scan-then-watch race window.
             */
            sourceWatcher.initialize();
            performInitialScan();
            running.set(true);
            sourceWatcher.start();

        } catch (IOException | RuntimeException exception) {
            running.set(false);
            close();
            throw exception;
        }
    }

    public void requestFullRescan() {
        ensureRunning();

        if (!fullRescanPending.compareAndSet(false, true)) {
            return;
        }

        submitReconciliation(() -> {
            try {
                reconcileSourceTree();
            } finally {
                fullRescanPending.set(false);
            }
        });
    }

    public boolean awaitIdle(Duration timeout)
            throws InterruptedException {

        Duration normalizedTimeout = Objects.requireNonNull(timeout);
        if (normalizedTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout cannot be negative");
        }

        long deadline = System.nanoTime() + normalizedTimeout.toNanos();

        while (System.nanoTime() <= deadline) {
            if (reconciliationTasks.get() == 0
                    && !fullRescanPending.get()
                    && scanScheduler.getTrackedPathCount() == 0) {
                return true;
            }

            Thread.sleep(5L);
        }

        return reconciliationTasks.get() == 0
                && !fullRescanPending.get()
                && scanScheduler.getTrackedPathCount() == 0;
    }

    public synchronized void stop() {
        close();
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        running.set(false);
        sourceWatcher.close();

        reconciliationExecutor.shutdownNow();
        try {
            reconciliationExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        scanScheduler.close();
    }

    private void performInitialScan() throws IOException {
        Files.walkFileTree(
                sourceRoot,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attributes
                    ) {
                        if (attributes.isRegularFile()) {
                            scanInitialFile(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                }
        );
    }

    private void scanInitialFile(Path file) {
        try {
            fileScanner.scan(file);
        } catch (FileChangedDuringScanException exception) {
            stateRegistry.setState(
                    file,
                    FileState.UNSCANNED
            );
        } catch (Exception exception) {
            handleScanFailure(file, exception);
        }
    }

    private void scheduleChangedFile(Path file) {
        Path normalized = normalize(file);

        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        stateRegistry.setState(
                normalized,
                FileState.UNSCANNED
        );

        try {
            scanScheduler.schedule(normalized);
        } catch (IllegalStateException ignored) {
            // Runtime is shutting down.
        }
    }

    private void scheduleSubtree(Path directory) {
        submitReconciliation(() -> {
            try {
                Files.walkFileTree(
                        directory,
                        new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(
                                    Path file,
                                    BasicFileAttributes attributes
                            ) {
                                if (attributes.isRegularFile()) {
                                    scheduleChangedFile(file);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        }
                );
            } catch (NoSuchFileException ignored) {
                // Directory disappeared before reconciliation.
            } catch (IOException exception) {
                handleWatcherFailure(exception);
            }
        });
    }

    private void reconcileSourceTree() {
        Set<Path> existingFiles = new HashSet<>();

        try {
            Files.walkFileTree(
                    sourceRoot,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(
                                Path file,
                                BasicFileAttributes attributes
                        ) {
                            if (attributes.isRegularFile()) {
                                Path normalized = normalize(file);
                                existingFiles.add(normalized);
                                scheduleChangedFile(normalized);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    }
            );

            removeMissingSourceStates(existingFiles);

        } catch (IOException exception) {
            handleWatcherFailure(exception);
        }
    }

    private void removeMissingSourceStates(Set<Path> existingFiles) {
        Map<Path, FileState> snapshot = stateRegistry.snapshot();

        for (Map.Entry<Path, FileState> entry : snapshot.entrySet()) {
            Path path = entry.getKey();

            if (!path.startsWith(sourceRoot)) {
                continue;
            }

            if (entry.getValue() == FileState.QUARANTINED) {
                continue;
            }

            if (!existingFiles.contains(path)) {
                stateRegistry.remove(path);
            }
        }
    }

    private void handleSourceFileDeletion(Path path) {
        Path normalized = normalize(path);

        stateRegistry.getState(normalized).ifPresent(state -> {
            if (state != FileState.QUARANTINED) {
                stateRegistry.remove(normalized);
            }
        });

        logBestEffort(
                LogEventType.SOURCE_DELETE_OBSERVED,
                normalized,
                normalized,
                "Source file deletion observed"
        );
    }

    private void handleSourceDirectoryDeletion(Path directory) {
        Path normalizedDirectory = normalize(directory);

        for (Map.Entry<Path, FileState> entry
                : stateRegistry.snapshot().entrySet()) {

            if (entry.getKey().startsWith(normalizedDirectory)
                    && entry.getValue() != FileState.QUARANTINED) {
                stateRegistry.remove(entry.getKey());
            }
        }

        logBestEffort(
                LogEventType.SOURCE_DELETE_OBSERVED,
                normalizedDirectory,
                normalizedDirectory,
                "Source directory deletion observed"
        );
    }

    private void handleScanFailure(
            Path path,
            Exception exception
    ) {
        logBestEffort(
                LogEventType.SCAN_FAILED,
                path,
                path,
                "Scan failed: " + exception.getMessage()
        );
    }

    private void handleWatcherFailure(Exception exception) {
        logBestEffort(
                LogEventType.WATCHER_FAILED,
                sourceRoot,
                sourceRoot,
                "Source watcher failure: " + exception.getMessage()
        );
    }

    private void submitReconciliation(Runnable task) {
        if (!running.get() && started.get()) {
            return;
        }

        reconciliationTasks.incrementAndGet();

        try {
            reconciliationExecutor.execute(() -> {
                try {
                    task.run();
                } finally {
                    reconciliationTasks.decrementAndGet();
                }
            });
        } catch (RuntimeException exception) {
            reconciliationTasks.decrementAndGet();

            if (!closed.get()) {
                throw exception;
            }
        }
    }

    private void logBestEffort(
            LogEventType type,
            Path originalPath,
            Path currentPath,
            String message
    ) {
        try {
            logHandler.log(
                    LogEntry.create(
                            type,
                            originalPath,
                            currentPath,
                            message
                    )
            );
        } catch (IOException ignored) {
            // Runtime processing must not die because diagnostic logging failed.
        }
    }

    private void ensureRunning() {
        ensureOpen();

        if (!running.get()) {
            throw new IllegalStateException(
                    "FileSecurityRuntime is not running"
            );
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "FileSecurityRuntime is closed"
            );
        }
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }

    private final class RuntimeWatchListener
            implements SourceWatchListener {

        @Override
        public void onFileChanged(Path path) {
            if (running.get()) {
                scheduleChangedFile(path);
            }
        }

        @Override
        public void onFileDeleted(Path path) {
            if (running.get()) {
                handleSourceFileDeletion(path);
            }
        }

        @Override
        public void onDirectoryCreated(Path path) {
            if (running.get()) {
                scheduleSubtree(path);
            }
        }

        @Override
        public void onDirectoryDeleted(Path path) {
            if (running.get()) {
                handleSourceDirectoryDeletion(path);
            }
        }

        @Override
        public void onOverflow(Path directory) {
            if (!running.get()) {
                return;
            }

            logBestEffort(
                    LogEventType.WATCH_OVERFLOW,
                    directory,
                    directory,
                    "WatchService overflow; scheduling full source reconciliation"
            );

            requestFullRescan();
        }

        @Override
        public void onWatcherError(Exception exception) {
            handleWatcherFailure(exception);
        }
    }
}
