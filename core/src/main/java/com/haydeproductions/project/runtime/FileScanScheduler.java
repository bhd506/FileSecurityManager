package com.haydeproductions.project.runtime;

import com.haydeproductions.project.scan.FileChangedDuringScanException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileScanScheduler implements AutoCloseable {

    private final ScanOperation scanOperation;
    private final ScanErrorHandler errorHandler;
    private final long debounceNanos;
    private final ScheduledExecutorService debounceExecutor;
    private final ExecutorService scanExecutor;
    private final ConcurrentMap<Path, Entry> entries =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public FileScanScheduler(
            ScanOperation scanOperation,
            Duration debounce,
            int workerThreads
    ) {
        this(
                scanOperation,
                debounce,
                workerThreads,
                (path, exception) -> { }
        );
    }

    public FileScanScheduler(
            ScanOperation scanOperation,
            Duration debounce,
            int workerThreads,
            ScanErrorHandler errorHandler
    ) {
        this.scanOperation = Objects.requireNonNull(scanOperation);
        this.errorHandler = Objects.requireNonNull(errorHandler);

        Duration normalizedDebounce = Objects.requireNonNull(debounce);
        if (normalizedDebounce.isNegative()) {
            throw new IllegalArgumentException("debounce cannot be negative");
        }
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("workerThreads must be positive");
        }

        this.debounceNanos = normalizedDebounce.toNanos();

        this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(
                runnable -> daemonThread(runnable, "file-scan-debounce")
        );

        this.scanExecutor = Executors.newFixedThreadPool(
                workerThreads,
                runnable -> daemonThread(runnable, "file-scan-worker")
        );
    }

    public void schedule(Path path) {
        if (closed.get()) {
            throw new IllegalStateException("FileScanScheduler is closed");
        }

        Path normalizedPath = normalize(path);
        Entry entry = entries.computeIfAbsent(
                normalizedPath,
                ignored -> new Entry()
        );

        synchronized (entry) {
            entry.dirty = true;

            if (entry.running) {
                return;
            }

            scheduleDebouncedLocked(normalizedPath, entry);
        }
    }

    public int getTrackedPathCount() {
        return entries.size();
    }

    public boolean awaitIdle(Duration timeout)
            throws InterruptedException {

        Duration normalizedTimeout = Objects.requireNonNull(timeout);
        if (normalizedTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout cannot be negative");
        }

        long deadline = System.nanoTime() + normalizedTimeout.toNanos();

        while (System.nanoTime() <= deadline) {
            if (entries.isEmpty()) {
                return true;
            }

            Thread.sleep(5L);
        }

        return entries.isEmpty();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        List<Entry> snapshot = new ArrayList<>(entries.values());

        for (Entry entry : snapshot) {
            synchronized (entry) {
                if (entry.pending != null) {
                    entry.pending.cancel(false);
                    entry.pending = null;
                }
                entry.dirty = false;
            }
        }

        debounceExecutor.shutdownNow();
        scanExecutor.shutdown();

        try {
            if (!scanExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scanExecutor.shutdownNow();
                scanExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            scanExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        entries.clear();
    }

    private void scheduleDebouncedLocked(
            Path path,
            Entry entry
    ) {
        if (entry.pending != null) {
            entry.pending.cancel(false);
        }

        entry.pending = debounceExecutor.schedule(
                () -> launch(path, entry),
                debounceNanos,
                TimeUnit.NANOSECONDS
        );
    }

    private void launch(Path path, Entry entry) {
        synchronized (entry) {
            entry.pending = null;

            if (closed.get()) {
                entries.remove(path, entry);
                return;
            }

            if (entry.running) {
                return;
            }

            entry.running = true;
            entry.dirty = false;
        }

        try {
            scanExecutor.execute(
                    () -> runScan(path, entry)
            );
        } catch (RejectedExecutionException exception) {
            synchronized (entry) {
                entry.running = false;
                entries.remove(path, entry);
            }
        }
    }

    private void runScan(Path path, Entry entry) {
        boolean retry = false;

        try {
            scanOperation.scan(path);

        } catch (FileChangedDuringScanException exception) {
            retry = true;

        } catch (Exception exception) {
            errorHandler.onScanError(path, exception);

        } finally {
            synchronized (entry) {
                entry.running = false;

                if (retry) {
                    entry.dirty = true;
                }

                if (!closed.get() && entry.dirty) {
                    scheduleDebouncedLocked(path, entry);
                } else {
                    entries.remove(path, entry);
                }
            }
        }
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }

    private static Thread daemonThread(
            Runnable runnable,
            String name
    ) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static final class Entry {
        private boolean running;
        private boolean dirty;
        private ScheduledFuture<?> pending;
    }
}
