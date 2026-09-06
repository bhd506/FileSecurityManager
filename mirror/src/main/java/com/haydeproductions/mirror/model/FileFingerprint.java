package com.haydeproductions.mirror.model;

import java.util.Objects;

public record FileFingerprint(boolean exists, long size, String sha256) {
    private static final FileFingerprint ABSENT = new FileFingerprint(false, 0L, "");

    public FileFingerprint {
        Objects.requireNonNull(sha256, "sha256");
        if (!exists && (size != 0L || !sha256.isEmpty())) {
            throw new IllegalArgumentException("Absent fingerprints cannot contain file data");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }
    }

    public static FileFingerprint absent() {
        return ABSENT;
    }

    public static FileFingerprint present(long size, String sha256) {
        return new FileFingerprint(true, size, sha256);
    }
}
