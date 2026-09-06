package com.haydeproductions.project.mirror;

import com.haydeproductions.mirror.api.MirrorErrorHandler;
import com.haydeproductions.mirror.api.MirrorService;
import com.haydeproductions.mirror.api.MirrorServices;
import com.haydeproductions.mirror.config.MirrorConfig;
import com.haydeproductions.project.config.MirrorDefinition;
import com.haydeproductions.project.log.LogEntry;
import com.haydeproductions.project.log.LogEventType;
import com.haydeproductions.project.log.LogHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns all configured mirror services and exposes the security core's
 * authorization-oriented view of mirroring.
 */
public final class MirrorManager implements AutoCloseable {

    private static final MirrorManager NONE = new MirrorManager(Map.of(), null);

    private final Map<String, MirrorService> services;
    private final LogHandler logHandler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private MirrorManager(
            Map<String, MirrorService> services,
            LogHandler logHandler
    ) {
        this.services = Map.copyOf(services);
        this.logHandler = logHandler;
    }

    public static MirrorManager none() {
        return NONE;
    }

    public static MirrorManager create(
            Path sourceRoot,
            Path mirrorBaseRoot,
            Path stateRoot,
            List<MirrorDefinition> definitions,
            LogHandler logHandler
    ) throws IOException {
        Objects.requireNonNull(sourceRoot);
        Objects.requireNonNull(mirrorBaseRoot);
        Objects.requireNonNull(stateRoot);
        Objects.requireNonNull(definitions);
        Objects.requireNonNull(logHandler);

        if (definitions.isEmpty()) {
            return new MirrorManager(Map.of(), logHandler);
        }

        Path normalizedSource = normalize(sourceRoot);
        Path normalizedMirrorBase = normalize(mirrorBaseRoot);
        Path normalizedStateRoot = normalize(stateRoot);

        Map<String, Path> roots = new LinkedHashMap<>();
        for (MirrorDefinition definition : definitions) {
            Path destination = normalizedMirrorBase
                    .resolve(definition.path())
                    .normalize();
            if (!destination.startsWith(normalizedMirrorBase)) {
                throw new IOException(
                        "Mirror destination escapes mirror base root: " + definition.id()
                );
            }
            for (Map.Entry<String, Path> existing : roots.entrySet()) {
                if (destination.equals(existing.getValue())
                        || destination.startsWith(existing.getValue())
                        || existing.getValue().startsWith(destination)) {
                    throw new IOException(
                            "Mirror destinations must not overlap: "
                                    + existing.getKey() + " and " + definition.id()
                    );
                }
            }
            roots.put(definition.id(), destination);
        }

        Map<String, MirrorService> services = new LinkedHashMap<>();
        try {
            for (MirrorDefinition definition : definitions) {
                MirrorConfig.Builder builder = MirrorConfig.builder(
                                normalizedSource,
                                roots.get(definition.id())
                        )
                        .mode(definition.mode())
                        .transferMode(definition.transferMode())
                        .conflictPolicy(definition.conflictPolicy())
                        .debounce(definition.debounce())
                        .maxTransferAttempts(definition.maxTransferAttempts())
                        // The security runtime is the authority for source changes.
                        // This prevents an already-authorized file version from being
                        // mirrored before a modified version is rescanned.
                        .watchSourceChanges(false);

                if (definition.persistentState()) {
                    builder.stateFile(
                            normalizedStateRoot.resolve(definition.id() + ".properties")
                    );
                }

                MirrorErrorHandler errorHandler = error -> logMirrorFailureBestEffort(
                        logHandler,
                        normalizedSource,
                        definition.id(),
                        error
                );

                MirrorService service = MirrorServices.create(
                        builder.build(),
                        errorHandler
                );
                services.put(definition.id(), service);
            }
        } catch (IOException | RuntimeException exception) {
            closeCreated(services.values(), exception);
            throw exception;
        }

        return new MirrorManager(services, logHandler);
    }

    public Set<String> ids() {
        return services.keySet();
    }

    public boolean isConfigured(String id) {
        return services.containsKey(Objects.requireNonNull(id));
    }

    public synchronized void start() throws IOException {
        ensureOpen();
        if (!started.compareAndSet(false, true)) {
            return;
        }

        List<MirrorService> startedServices = new ArrayList<>();
        try {
            for (MirrorService service : services.values()) {
                service.start();
                startedServices.add(service);
            }
        } catch (IOException | RuntimeException exception) {
            started.set(false);
            closeCreated(startedServices, exception);
            throw exception;
        }
    }

    public void allow(Path sourceFile, Collection<String> mirrorIds)
            throws IOException {
        ensureOpen();
        Objects.requireNonNull(sourceFile);
        List<String> ids = validatedIds(mirrorIds);
        List<MirrorService> newlyAdded = new ArrayList<>();
        IOException failure = null;

        for (String id : ids) {
            MirrorService service = services.get(id);
            boolean wasTarget = service.isTarget(sourceFile);
            try {
                service.addTarget(sourceFile);
                if (!wasTarget) {
                    newlyAdded.add(service);
                }
            } catch (IOException exception) {
                failure = exception;
                break;
            }
        }

        if (failure != null) {
            for (MirrorService service : newlyAdded) {
                try {
                    service.suspendTarget(sourceFile);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    public void deny(Path sourceFile, Collection<String> mirrorIds)
            throws IOException {
        ensureOpen();
        Objects.requireNonNull(sourceFile);
        IOException failure = null;
        for (String id : validatedIds(mirrorIds)) {
            try {
                services.get(id).suspendTarget(sourceFile);
            } catch (IOException exception) {
                failure = append(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public void revokeAll(Path sourceFile) throws IOException {
        if (this != NONE) {
            ensureOpen();
        }
        Objects.requireNonNull(sourceFile);
        IOException failure = null;
        for (MirrorService service : services.values()) {
            try {
                service.suspendTarget(sourceFile);
            } catch (IOException exception) {
                failure = append(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public void revokeUnder(Path directory) throws IOException {
        if (this != NONE) {
            ensureOpen();
        }
        Path normalizedDirectory = normalize(directory);
        IOException failure = null;
        for (MirrorService service : services.values()) {
            for (Path target : service.targets()) {
                if (target.startsWith(normalizedDirectory)) {
                    try {
                        service.suspendTarget(target);
                    } catch (IOException exception) {
                        failure = append(failure, exception);
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public Set<String> activeMirrorIds(Path sourceFile) throws IOException {
        if (this != NONE) {
            ensureOpen();
        }
        Objects.requireNonNull(sourceFile);
        Set<String> active = new LinkedHashSet<>();
        for (Map.Entry<String, MirrorService> entry : services.entrySet()) {
            if (entry.getValue().isTarget(sourceFile)) {
                active.add(entry.getKey());
            }
        }
        return Set.copyOf(active);
    }

    @Override
    public synchronized void close() {
        if (this == NONE || !closed.compareAndSet(false, true)) {
            return;
        }

        List<IOException> failures = new ArrayList<>();
        List<MirrorService> reverse = new ArrayList<>(services.values());
        java.util.Collections.reverse(reverse);
        for (MirrorService service : reverse) {
            try {
                service.close();
            } catch (IOException exception) {
                failures.add(exception);
            }
        }

        if (!failures.isEmpty() && logHandler != null) {
            for (IOException failure : failures) {
                logMirrorFailureBestEffort(
                        logHandler,
                        Path.of("."),
                        "shutdown",
                        failure
                );
            }
        }
    }

    private List<String> validatedIds(Collection<String> mirrorIds) {
        Objects.requireNonNull(mirrorIds);
        List<String> ids = new ArrayList<>();
        for (String id : mirrorIds) {
            String value = Objects.requireNonNull(id);
            if (!services.containsKey(value)) {
                throw new IllegalArgumentException("Unknown mirror id: " + value);
            }
            ids.add(value);
        }
        return List.copyOf(ids);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("MirrorManager is closed");
        }
    }

    private static IOException append(IOException current, IOException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static void closeCreated(
            Collection<MirrorService> created,
            Throwable original
    ) {
        for (MirrorService service : created) {
            try {
                service.close();
            } catch (IOException closeFailure) {
                original.addSuppressed(closeFailure);
            }
        }
    }

    private static void logMirrorFailureBestEffort(
            LogHandler logHandler,
            Path sourceRoot,
            String mirrorId,
            Throwable error
    ) {
        try {
            logHandler.log(
                    LogEntry.create(
                            LogEventType.MIRROR_FAILED,
                            sourceRoot,
                            sourceRoot,
                            "Mirror " + mirrorId + " failed: " + error.getMessage()
                    )
            );
        } catch (IOException ignored) {
            // The mirror failure remains available to the caller/error handler.
        }
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path).toAbsolutePath().normalize();
    }
}
