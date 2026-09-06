package com.haydeproductions.mirror.transfer;

import com.haydeproductions.mirror.core.OperationSuppressor;
import com.haydeproductions.mirror.exception.InvalidMirrorTargetException;
import com.haydeproductions.mirror.model.FileFingerprint;
import com.haydeproductions.mirror.model.MirrorSide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotAndTransferTest {
    @TempDir Path temp;

    @Test
    void fingerprintsEqualContentEquallyAndDifferentContentDifferently() throws Exception {
        SnapshotService snapshots = new SnapshotService(new FileHasher());
        Path a = temp.resolve("a.txt");
        Path b = temp.resolve("b.txt");
        Files.writeString(a, "hello");
        Files.writeString(b, "hello");
        assertEquals(snapshots.fingerprint(a), snapshots.fingerprint(b));
        Files.writeString(b, "world");
        assertNotEquals(snapshots.fingerprint(a), snapshots.fingerprint(b));
    }

    @Test
    void missingFileProducesAbsentFingerprint() throws Exception {
        SnapshotService snapshots = new SnapshotService(new FileHasher());
        assertEquals(FileFingerprint.absent(), snapshots.fingerprint(temp.resolve("missing")));
    }

    @Test
    void directoryIsRejected() throws Exception {
        SnapshotService snapshots = new SnapshotService(new FileHasher());
        assertThrows(InvalidMirrorTargetException.class, () -> snapshots.fingerprint(temp));
    }

    @Test
    void copyCreatesParentsAndReplacesDestination() throws Exception {
        SnapshotService snapshots = new SnapshotService(new FileHasher());
        OperationSuppressor suppressor = new OperationSuppressor(Duration.ofSeconds(1));
        TransferEngine transfers = new TransferEngine(snapshots, suppressor, 3);
        Path source = temp.resolve("source.txt");
        Path destination = temp.resolve("nested/mirror.txt");
        Files.writeString(source, "first");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "old");
        FileFingerprint result = transfers.copy(source, destination, MirrorSide.MIRROR, Path.of("mirror.txt"));
        assertEquals("first", Files.readString(destination));
        assertEquals(snapshots.fingerprint(source), result);
        assertTrue(suppressor.shouldSuppress(MirrorSide.MIRROR, Path.of("mirror.txt"), result));
    }

    @Test
    void moveCopiesThenDeletesSource() throws Exception {
        SnapshotService snapshots = new SnapshotService(new FileHasher());
        OperationSuppressor suppressor = new OperationSuppressor(Duration.ofSeconds(1));
        TransferEngine transfers = new TransferEngine(snapshots, suppressor, 3);
        Path source = temp.resolve("source.txt");
        Path destination = temp.resolve("mirror.txt");
        Files.writeString(source, "payload");
        transfers.move(source, destination, MirrorSide.SOURCE, MirrorSide.MIRROR, Path.of("file.txt"));
        assertFalse(Files.exists(source));
        assertEquals("payload", Files.readString(destination));
        assertTrue(suppressor.shouldSuppress(MirrorSide.SOURCE, Path.of("file.txt"), FileFingerprint.absent()));
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        SnapshotService snapshots = new SnapshotService(new FileHasher());
        OperationSuppressor suppressor = new OperationSuppressor(Duration.ofSeconds(1));
        TransferEngine transfers = new TransferEngine(snapshots, suppressor, 3);
        Path file = temp.resolve("file.txt");
        Files.writeString(file, "x");
        transfers.delete(file, MirrorSide.MIRROR, Path.of("file.txt"));
        transfers.delete(file, MirrorSide.MIRROR, Path.of("file.txt"));
        assertFalse(Files.exists(file));
    }
}
