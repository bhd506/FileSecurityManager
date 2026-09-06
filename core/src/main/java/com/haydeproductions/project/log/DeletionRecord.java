package com.haydeproductions.project.log;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeletionRecord(
        String deletionId,
        Instant deletedAt,
        Path originalPath,
        Path deletedPath,
        long size,
        String sha256
) {
    public DeletionRecord {
        deletionId = Objects.requireNonNull(deletionId);
        deletedAt = Objects.requireNonNull(deletedAt);
        originalPath = normalize(originalPath);
        deletedPath = normalize(deletedPath);

        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }

        sha256 = Objects.requireNonNull(sha256).toLowerCase();
    }

    public static DeletionRecord create(
            Path originalPath,
            Path deletedPath,
            long size,
            String sha256
    ) {
        return new DeletionRecord(
                UUID.randomUUID().toString(),
                Instant.now(),
                originalPath,
                deletedPath,
                size,
                sha256
        );
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }
}
