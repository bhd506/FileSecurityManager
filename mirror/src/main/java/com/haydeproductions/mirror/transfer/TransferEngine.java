package com.haydeproductions.mirror.transfer;

import com.haydeproductions.mirror.core.OperationSuppressor;
import com.haydeproductions.mirror.model.FileFingerprint;
import com.haydeproductions.mirror.model.MirrorSide;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class TransferEngine {
    private final SnapshotService snapshots;
    private final OperationSuppressor suppressor;
    private final int maxAttempts;

    public TransferEngine(
            SnapshotService snapshots,
            OperationSuppressor suppressor,
            int maxAttempts
    ) {
        this.snapshots = snapshots;
        this.suppressor = suppressor;
        this.maxAttempts = maxAttempts;
    }

    public FileFingerprint copy(
            Path from,
            Path to,
            MirrorSide destinationSide,
            Path relative
    ) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return copyOnce(from, to, destinationSide, relative);
            } catch (SourceChangedDuringTransferException e) {
                last = e;
            }
        }
        throw last == null
                ? new IOException("Unable to copy " + from + " to " + to)
                : last;
    }

    public FileFingerprint move(
            Path from,
            Path to,
            MirrorSide sourceSide,
            MirrorSide destinationSide,
            Path relative
    ) throws IOException {
        FileFingerprint destination = copy(from, to, destinationSide, relative);
        Files.delete(from);
        suppressor.expect(sourceSide, relative, FileFingerprint.absent());
        return destination;
    }

    public void delete(
            Path path,
            MirrorSide side,
            Path relative
    ) throws IOException {
        Files.deleteIfExists(path);
        suppressor.expect(side, relative, FileFingerprint.absent());
    }

    private FileFingerprint copyOnce(
            Path from,
            Path to,
            MirrorSide destinationSide,
            Path relative
    ) throws IOException {
        FileFingerprint before = snapshots.fingerprint(from);
        if (!before.exists()) {
            throw new IOException("Source disappeared before transfer: " + from);
        }

        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp = to.resolveSibling(to.getFileName() + ".mirror-tmp-" + UUID.randomUUID());
        try {
            Files.copy(from, temp, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);

            FileFingerprint tempFingerprint = snapshots.fingerprint(temp);
            FileFingerprint after = snapshots.fingerprint(from);
            if (!after.exists() || !after.equals(tempFingerprint)) {
                throw new SourceChangedDuringTransferException(from);
            }

            try {
                Files.move(
                        temp,
                        to,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING);
            }

            FileFingerprint committed = snapshots.fingerprint(to);
            if (!committed.equals(after)) {
                throw new IOException("Destination verification failed: " + to);
            }
            suppressor.expect(destinationSide, relative, committed);
            return committed;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static final class SourceChangedDuringTransferException extends IOException {
        private SourceChangedDuringTransferException(Path source) {
            super("Source changed during transfer: " + source);
        }
    }
}
