package com.haydeproductions.project.log;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LogEntry(
        String eventId,
        Instant timestamp,
        LogEventType type,
        Path originalPath,
        Path currentPath,
        String message
) {
    public LogEntry {
        eventId = Objects.requireNonNull(eventId);
        timestamp = Objects.requireNonNull(timestamp);
        type = Objects.requireNonNull(type);
        originalPath = normalize(originalPath);
        currentPath = normalize(currentPath);
        message = Objects.requireNonNull(message);
    }

    public static LogEntry create(
            LogEventType type,
            Path originalPath,
            Path currentPath,
            String message
    ) {
        return new LogEntry(
                UUID.randomUUID().toString(),
                Instant.now(),
                type,
                originalPath,
                currentPath,
                message
        );
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }
}
