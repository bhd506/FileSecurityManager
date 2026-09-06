package com.haydeproductions.mirror.watch;

import com.haydeproductions.mirror.api.MirrorErrorHandler;
import com.haydeproductions.mirror.model.MirrorEvent;
import com.haydeproductions.mirror.model.MirrorSide;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class MirrorWatcher implements AutoCloseable {
    private final Path sourceRoot;
    private final Path mirrorRoot;
    private final Duration debounce;
    private final Consumer<MirrorEvent> eventHandler;
    private final Consumer<MirrorSide> overflowHandler;
    private final MirrorErrorHandler errorHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<WatchKey, Registration> registrations = new ConcurrentHashMap<>();
    private final Map<EventKey, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    private WatchService watchService;
    private ScheduledExecutorService scheduler;
    private Thread watchThread;

    public MirrorWatcher(
            Path sourceRoot,
            Path mirrorRoot,
            Duration debounce,
            Consumer<MirrorEvent> eventHandler,
            Consumer<MirrorSide> overflowHandler,
            MirrorErrorHandler errorHandler
    ) {
        this.sourceRoot = sourceRoot;
        this.mirrorRoot = mirrorRoot;
        this.debounce = debounce;
        this.eventHandler = eventHandler;
        this.overflowHandler = overflowHandler;
        this.errorHandler = errorHandler;
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        watchService = sourceRoot.getFileSystem().newWatchService();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "mirror-debounce");
            thread.setDaemon(true);
            return thread;
        });

        registerTree(sourceRoot, MirrorSide.SOURCE);
        registerTree(mirrorRoot, MirrorSide.MIRROR);

        running.set(true);
        watchThread = new Thread(this::watchLoop, "mirror-watch");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void stop() throws IOException {
        if (!running.getAndSet(false)) {
            return;
        }

        for (ScheduledFuture<?> future : pending.values()) {
            future.cancel(false);
        }
        pending.clear();

        IOException failure = null;

        // Stop the WatchService first so the watch thread wakes up.
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                failure = e;
            }
        }

        // Wait for the watch thread to actually exit.
        if (watchThread != null && watchThread != Thread.currentThread()) {
            try {
                watchThread.join(5000);

                if (watchThread.isAlive()) {
                    IOException e =
                            new IOException("Mirror watch thread did not terminate");

                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                IOException io =
                        new IOException(
                                "Interrupted while stopping mirror watcher",
                                e
                        );

                if (failure == null) {
                    failure = io;
                } else {
                    failure.addSuppressed(io);
                }
            }
        }

        // Then stop and wait for debounce/reconciliation tasks.
        if (scheduler != null) {
            scheduler.shutdownNow();

            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    IOException e =
                            new IOException(
                                    "Mirror debounce scheduler did not terminate"
                            );

                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                IOException io =
                        new IOException(
                                "Interrupted while stopping mirror scheduler",
                                e
                        );

                if (failure == null) {
                    failure = io;
                } else {
                    failure.addSuppressed(io);
                }
            }
        }

        registrations.clear();

        watchService = null;
        scheduler = null;
        watchThread = null;

        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    private void watchLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                Registration registration = registrations.get(key);
                if (registration == null) {
                    key.reset();
                    continue;
                }

                processEvents(key, registration);
                if (!key.reset()) {
                    registrations.remove(key);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                return;
            } catch (Throwable error) {
                errorHandler.onError(error);
            }
        }
    }

    private void processEvents(WatchKey key, Registration registration) {
        for (WatchEvent<?> rawEvent : key.pollEvents()) {
            WatchEvent.Kind<?> kind = rawEvent.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                overflowHandler.accept(registration.side());
                continue;
            }

            @SuppressWarnings("unchecked")
            WatchEvent<Path> event = (WatchEvent<Path>) rawEvent;
            Path absolute = registration.directory().resolve(event.context()).normalize();

            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                tryRegisterCreatedDirectory(absolute, registration.side());
            }

            Path root = registration.side() == MirrorSide.SOURCE ? sourceRoot : mirrorRoot;
            if (!absolute.startsWith(root)) {
                continue;
            }
            Path relative = root.relativize(absolute).normalize();
            if (relative.getNameCount() == 0) {
                continue;
            }
            debounce(new MirrorEvent(registration.side(), relative));
        }
    }

    private void debounce(MirrorEvent event) {
        EventKey key = new EventKey(event.side(), event.relativePath());
        ScheduledFuture<?> previous = pending.remove(key);
        if (previous != null) {
            previous.cancel(false);
        }

        long delayMillis = debounce.toMillis();
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pending.remove(key);
            try {
                eventHandler.accept(event);
            } catch (Throwable error) {
                errorHandler.onError(error);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        pending.put(key, future);
    }

    private void tryRegisterCreatedDirectory(Path path, MirrorSide side) {
        try {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) {
                registerTree(path, side);
            }
        } catch (IOException e) {
            errorHandler.onError(e);
        }
    }

    private void registerTree(Path root, MirrorSide side) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                registerDirectory(dir, side);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerDirectory(Path directory, MirrorSide side) throws IOException {
        WatchKey key = directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );
        registrations.put(key, new Registration(directory, side));
    }

    private record Registration(Path directory, MirrorSide side) {
    }

    private record EventKey(MirrorSide side, Path relative) {
        private EventKey {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(relative, "relative");
        }
    }
}
