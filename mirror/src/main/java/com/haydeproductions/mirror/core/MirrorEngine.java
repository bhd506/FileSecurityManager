package com.haydeproductions.mirror.core;

import com.haydeproductions.mirror.config.ConflictPolicy;
import com.haydeproductions.mirror.config.MirrorConfig;
import com.haydeproductions.mirror.config.MirrorMode;
import com.haydeproductions.mirror.config.TransferMode;
import com.haydeproductions.mirror.exception.InvalidMirrorTargetException;
import com.haydeproductions.mirror.exception.MirrorConflictException;
import com.haydeproductions.mirror.model.FileFingerprint;
import com.haydeproductions.mirror.model.MirrorSide;
import com.haydeproductions.mirror.state.SyncStateStore;
import com.haydeproductions.mirror.transfer.SnapshotService;
import com.haydeproductions.mirror.transfer.TransferEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public final class MirrorEngine {
    private final MirrorConfig config;
    private final MirrorPathMapper mapper;
    private final MirrorTargetRegistry targets;
    private final SnapshotService snapshots;
    private final TransferEngine transfers;
    private final SyncStateStore syncState;
    private static final int LOCK_STRIPES = 256;

    private final OperationSuppressor suppressor;
    private final Object[] locks = new Object[LOCK_STRIPES];

    public MirrorEngine(
            MirrorConfig config,
            MirrorPathMapper mapper,
            MirrorTargetRegistry targets,
            SnapshotService snapshots,
            TransferEngine transfers,
            SyncStateStore syncState,
            OperationSuppressor suppressor
    ) {
        this.config = config;
        this.mapper = mapper;
        this.targets = targets;
        this.snapshots = snapshots;
        this.transfers = transfers;
        this.syncState = syncState;
        this.suppressor = suppressor;
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
    }

    public void reconcile(Path relative, MirrorSide triggerSide) throws IOException {
        Path normalised = relative.normalize();
        if (!targets.contains(normalised)) {
            return;
        }

        Object lock = locks[Math.floorMod(normalised.hashCode(), locks.length)];
        synchronized (lock) {
            reconcileLocked(normalised, triggerSide);
        }
    }

    public void reconcileAll(MirrorSide triggerSide) throws IOException {
        IOException failure = null;
        for (Path target : targets.snapshot()) {
            try {
                reconcile(target, triggerSide);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void reconcileLocked(Path relative, MirrorSide triggerSide) throws IOException {
        Path source = mapper.source(relative);
        Path mirror = mapper.mirror(relative);

        PathSafety.ensureNoSymlinkTraversal(config.sourceRoot(), source);
        PathSafety.ensureNoSymlinkTraversal(config.mirrorRoot(), mirror);

        if (triggerSide != null) {
            Path changed = triggerSide == MirrorSide.SOURCE ? source : mirror;
            FileFingerprint actual = snapshots.fingerprint(changed);
            if (suppressor.shouldSuppress(triggerSide, relative, actual)) {
                return;
            }
        }

        switch (config.mode()) {
            case ONE_TIME -> reconcileOneTime(relative, source, mirror, triggerSide);
            case SOURCE_TO_MIRROR -> reconcileAuthoritative(
                    relative,
                    source,
                    mirror,
                    MirrorSide.SOURCE,
                    MirrorSide.MIRROR
            );
            case MIRROR_TO_SOURCE -> reconcileAuthoritative(
                    relative,
                    mirror,
                    source,
                    MirrorSide.MIRROR,
                    MirrorSide.SOURCE
            );
            case BIDIRECTIONAL -> reconcileBidirectional(relative, source, mirror);
        }
    }

    private void reconcileOneTime(
            Path relative,
            Path source,
            Path mirror,
            MirrorSide triggerSide
    ) throws IOException {
        if (triggerSide == MirrorSide.MIRROR) {
            return;
        }

        FileFingerprint sourceFingerprint = snapshots.fingerprint(source);
        if (!sourceFingerprint.exists()) {
            return;
        }

        FileFingerprint mirrorFingerprint = snapshots.fingerprint(mirror);
        if (sourceFingerprint.equals(mirrorFingerprint)) {
            return;
        }

        if (config.transferMode() == TransferMode.MOVE) {
            transfers.move(
                    source,
                    mirror,
                    MirrorSide.SOURCE,
                    MirrorSide.MIRROR,
                    relative
            );
        } else {
            transfers.copy(source, mirror, MirrorSide.MIRROR, relative);
        }
    }

    private void reconcileAuthoritative(
            Path relative,
            Path authoritative,
            Path follower,
            MirrorSide authoritativeSide,
            MirrorSide followerSide
    ) throws IOException {
        FileFingerprint authoritativeFingerprint = snapshots.fingerprint(authoritative);
        FileFingerprint followerFingerprint = snapshots.fingerprint(follower);

        if (!authoritativeFingerprint.exists()) {
            if (followerFingerprint.exists()) {
                transfers.delete(follower, followerSide, relative);
            }
            return;
        }

        if (!authoritativeFingerprint.equals(followerFingerprint)) {
            transfers.copy(authoritative, follower, followerSide, relative);
        }
    }

    private void reconcileBidirectional(
            Path relative,
            Path source,
            Path mirror
    ) throws IOException {
        FileFingerprint sourceFingerprint = snapshots.fingerprint(source);
        FileFingerprint mirrorFingerprint = snapshots.fingerprint(mirror);

        if (sourceFingerprint.equals(mirrorFingerprint)) {
            syncState.put(relative, sourceFingerprint);
            return;
        }

        Optional<FileFingerprint> previousOptional = syncState.get(relative);
        if (previousOptional.isEmpty()) {
            reconcileInitialBidirectional(
                    relative,
                    source,
                    mirror,
                    sourceFingerprint,
                    mirrorFingerprint
            );
            return;
        }

        FileFingerprint previous = previousOptional.get();
        boolean sourceChanged = !sourceFingerprint.equals(previous);
        boolean mirrorChanged = !mirrorFingerprint.equals(previous);

        if (sourceChanged && !mirrorChanged) {
            applyState(
                    relative,
                    source,
                    mirror,
                    sourceFingerprint,
                    MirrorSide.MIRROR
            );
            return;
        }

        if (mirrorChanged && !sourceChanged) {
            applyState(
                    relative,
                    mirror,
                    source,
                    mirrorFingerprint,
                    MirrorSide.SOURCE
            );
            return;
        }

        if (sourceFingerprint.equals(mirrorFingerprint)) {
            syncState.put(relative, sourceFingerprint);
            return;
        }

        resolveConflict(relative, source, mirror, sourceFingerprint, mirrorFingerprint);
    }

    private void reconcileInitialBidirectional(
            Path relative,
            Path source,
            Path mirror,
            FileFingerprint sourceFingerprint,
            FileFingerprint mirrorFingerprint
    ) throws IOException {
        if (sourceFingerprint.exists() && !mirrorFingerprint.exists()) {
            applyState(relative, source, mirror, sourceFingerprint, MirrorSide.MIRROR);
            return;
        }
        if (!sourceFingerprint.exists() && mirrorFingerprint.exists()) {
            applyState(relative, mirror, source, mirrorFingerprint, MirrorSide.SOURCE);
            return;
        }
        if (!sourceFingerprint.exists()) {
            syncState.put(relative, FileFingerprint.absent());
            return;
        }

        resolveConflict(relative, source, mirror, sourceFingerprint, mirrorFingerprint);
    }

    private void resolveConflict(
            Path relative,
            Path source,
            Path mirror,
            FileFingerprint sourceFingerprint,
            FileFingerprint mirrorFingerprint
    ) throws IOException {
        ConflictPolicy policy = config.conflictPolicy();
        switch (policy) {
            case FAIL -> throw new MirrorConflictException(relative);
            case SOURCE_WINS -> applyState(
                    relative,
                    source,
                    mirror,
                    sourceFingerprint,
                    MirrorSide.MIRROR
            );
            case MIRROR_WINS -> applyState(
                    relative,
                    mirror,
                    source,
                    mirrorFingerprint,
                    MirrorSide.SOURCE
            );
        }
    }

    private void applyState(
            Path relative,
            Path from,
            Path to,
            FileFingerprint desired,
            MirrorSide destinationSide
    ) throws IOException {
        if (!desired.exists()) {
            transfers.delete(to, destinationSide, relative);
            syncState.put(relative, FileFingerprint.absent());
            return;
        }

        FileFingerprint committed = transfers.copy(from, to, destinationSide, relative);
        syncState.put(relative, committed);
    }
}
