package com.haydeproductions.mirror.api;

import com.haydeproductions.mirror.config.MirrorConfig;
import com.haydeproductions.mirror.core.MirrorEngine;
import com.haydeproductions.mirror.core.MirrorPathMapper;
import com.haydeproductions.mirror.core.MirrorTargetRegistry;
import com.haydeproductions.mirror.core.OperationSuppressor;
import com.haydeproductions.mirror.core.PathSafety;
import com.haydeproductions.mirror.model.MirrorEvent;
import com.haydeproductions.mirror.model.MirrorSide;
import com.haydeproductions.mirror.state.InMemorySyncStateStore;
import com.haydeproductions.mirror.state.PropertiesSyncStateStore;
import com.haydeproductions.mirror.state.SyncStateStore;
import com.haydeproductions.mirror.transfer.FileHasher;
import com.haydeproductions.mirror.transfer.SnapshotService;
import com.haydeproductions.mirror.transfer.TransferEngine;
import com.haydeproductions.mirror.watch.MirrorWatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

final class DefaultMirrorService implements MirrorService {
    private final MirrorConfig config;
    private final MirrorErrorHandler errorHandler;
    private final MirrorPathMapper mapper;
    private final MirrorTargetRegistry targetRegistry;
    private final SyncStateStore syncStateStore;
    private final SnapshotService snapshots;
    private final MirrorEngine engine;
    private final MirrorWatcher watcher;

    DefaultMirrorService(
            MirrorConfig config,
            MirrorErrorHandler errorHandler
    ) throws IOException {
        this.config = config;
        this.errorHandler = errorHandler;

        PathSafety.validateRoot(config.sourceRoot(), false);
        PathSafety.validateRoot(config.mirrorRoot(), true);
        PathSafety.ensureRootsDoNotOverlap(config.sourceRoot(), config.mirrorRoot());

        this.mapper = new MirrorPathMapper(config.sourceRoot(), config.mirrorRoot());
        this.targetRegistry = new MirrorTargetRegistry();
        this.snapshots = new SnapshotService(new FileHasher());

        Duration suppressionLifetime = Duration.ofMillis(
                Math.max(3000L, Math.max(1L, config.debounce().toMillis()) * 10L)
        );
        OperationSuppressor suppressor = new OperationSuppressor(suppressionLifetime);
        TransferEngine transfers = new TransferEngine(
                snapshots,
                suppressor,
                config.maxTransferAttempts()
        );

        this.syncStateStore = config.stateFile().isPresent()
                ? new PropertiesSyncStateStore(config.stateFile().orElseThrow())
                : new InMemorySyncStateStore();

        this.engine = new MirrorEngine(
                config,
                mapper,
                targetRegistry,
                snapshots,
                transfers,
                syncStateStore,
                suppressor
        );

        this.watcher = new MirrorWatcher(
                config.sourceRoot(),
                config.mirrorRoot(),
                config.debounce(),
                this::handleEvent,
                this::handleOverflow,
                errorHandler
        );
    }

    @Override
    public void addTarget(Path sourceFile) throws IOException {
        Path relative = mapper.toRelativeSource(sourceFile);
        Path source = mapper.source(relative);
        Path mirror = mapper.mirror(relative);

        PathSafety.ensureNoSymlinkTraversal(config.sourceRoot(), source);
        PathSafety.ensureNoSymlinkTraversal(config.mirrorRoot(), mirror);
        if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            snapshots.fingerprint(source);
        }

        boolean added = targetRegistry.add(relative);
        if (!added) {
            return;
        }
        try {
            engine.reconcile(relative, MirrorSide.SOURCE);
        } catch (IOException | RuntimeException e) {
            targetRegistry.remove(relative);
            syncStateStore.remove(relative);
            throw e;
        }
    }

    @Override
    public void removeTarget(Path sourceFile) throws IOException {
        Path relative = mapper.toRelativeSource(sourceFile);
        targetRegistry.remove(relative);
        syncStateStore.remove(relative);
    }

    @Override
    public boolean isTarget(Path sourceFile) throws IOException {
        return targetRegistry.contains(mapper.toRelativeSource(sourceFile));
    }

    @Override
    public Set<Path> targets() {
        Set<Path> absolute = new HashSet<>();
        for (Path relative : targetRegistry.snapshot()) {
            try {
                absolute.add(mapper.source(relative));
            } catch (IOException e) {
                throw new IllegalStateException("Registry contained an invalid target", e);
            }
        }
        return Set.copyOf(absolute);
    }

    @Override
    public void reconcileTarget(Path sourceFile) throws IOException {
        Path relative = mapper.toRelativeSource(sourceFile);
        engine.reconcile(relative, MirrorSide.SOURCE);
    }

    @Override
    public void reconcileAll() throws IOException {
        engine.reconcileAll(MirrorSide.SOURCE);
    }

    @Override
    public void start() throws IOException {
        watcher.start();
    }

    @Override
    public void stop() throws IOException {
        watcher.stop();
    }

    @Override
    public boolean isRunning() {
        return watcher.isRunning();
    }

    private void handleEvent(MirrorEvent event) {
        for (Path target : targetRegistry.affectedBy(event.relativePath())) {
            try {
                engine.reconcile(target, event.side());
            } catch (Throwable error) {
                errorHandler.onError(error);
            }
        }
    }

    private void handleOverflow(MirrorSide side) {
        try {
            engine.reconcileAll(side);
        } catch (Throwable error) {
            errorHandler.onError(error);
        }
    }
}
