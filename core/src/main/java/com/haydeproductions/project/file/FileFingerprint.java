package com.haydeproductions.project.file;

import java.util.Objects;

public record FileFingerprint(
        long size,
        String sha256
) {
    public FileFingerprint {
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }

        sha256 = Objects.requireNonNull(sha256).toLowerCase();
    }
}
