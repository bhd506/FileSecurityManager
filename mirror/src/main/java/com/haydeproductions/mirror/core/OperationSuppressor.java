package com.haydeproductions.mirror.core;

import com.haydeproductions.mirror.model.FileFingerprint;
import com.haydeproductions.mirror.model.MirrorSide;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class OperationSuppressor {
    private final ConcurrentMap<Key, Expected> expected = new ConcurrentHashMap<>();
    private final Duration lifetime;

    public OperationSuppressor(Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("Suppression lifetime must be positive");
        }
        this.lifetime = lifetime;
    }

    public void expect(MirrorSide side, Path relative, FileFingerprint fingerprint) {
        expected.put(
                new Key(side, relative.normalize()),
                new Expected(fingerprint, Instant.now().plus(lifetime))
        );
    }

    public boolean shouldSuppress(MirrorSide side, Path relative, FileFingerprint actual) {
        Key key = new Key(side, relative.normalize());
        Expected value = expected.get(key);
        if (value == null) {
            return false;
        }
        if (Instant.now().isAfter(value.expiresAt())) {
            expected.remove(key, value);
            return false;
        }
        if (value.fingerprint().equals(actual)) {
            // A single filesystem operation can emit several CREATE/MODIFY/DELETE
            // notifications. Keep suppressing the same resulting fingerprint until
            // the short lifetime expires. Any genuinely different fingerprint below
            // clears the expectation immediately.
            return true;
        }
        expected.remove(key, value);
        return false;
    }

    private record Key(MirrorSide side, Path relative) {
        private Key {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(relative, "relative");
        }
    }

    private record Expected(FileFingerprint fingerprint, Instant expiresAt) {
    }
}
