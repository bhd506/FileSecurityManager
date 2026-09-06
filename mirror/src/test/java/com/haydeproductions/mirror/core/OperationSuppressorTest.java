package com.haydeproductions.mirror.core;

import com.haydeproductions.mirror.model.FileFingerprint;
import com.haydeproductions.mirror.model.MirrorSide;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class OperationSuppressorTest {
    @Test
    void suppressesExpectedFingerprintOnly() {
        OperationSuppressor suppressor = new OperationSuppressor(Duration.ofSeconds(1));
        Path path = Path.of("a.txt");
        FileFingerprint expected = FileFingerprint.present(1, "abc");
        suppressor.expect(MirrorSide.MIRROR, path, expected);
        assertTrue(suppressor.shouldSuppress(MirrorSide.MIRROR, path, expected));
        assertFalse(suppressor.shouldSuppress(MirrorSide.SOURCE, path, expected));
        assertFalse(suppressor.shouldSuppress(MirrorSide.MIRROR, path, FileFingerprint.present(2, "def")));
    }

    @Test
    void expiryStopsSuppression() throws Exception {
        OperationSuppressor suppressor = new OperationSuppressor(Duration.ofMillis(5));
        Path path = Path.of("a.txt");
        FileFingerprint expected = FileFingerprint.absent();
        suppressor.expect(MirrorSide.SOURCE, path, expected);
        Thread.sleep(20);
        assertFalse(suppressor.shouldSuppress(MirrorSide.SOURCE, path, expected));
    }

    @Test
    void nonMatchingEventClearsStaleSuppression() {
        OperationSuppressor suppressor = new OperationSuppressor(Duration.ofSeconds(1));
        Path path = Path.of("a.txt");
        suppressor.expect(MirrorSide.MIRROR, path, FileFingerprint.absent());
        assertFalse(suppressor.shouldSuppress(
                MirrorSide.MIRROR, path, FileFingerprint.present(1, "new")
        ));
        assertFalse(suppressor.shouldSuppress(MirrorSide.MIRROR, path, FileFingerprint.absent()));
    }

    @Test
    void rejectsNonPositiveLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new OperationSuppressor(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new OperationSuppressor(Duration.ofMillis(-1)));
    }
}
