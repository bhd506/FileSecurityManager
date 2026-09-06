package com.haydeproductions.project.config;

import com.haydeproductions.mirror.config.ConflictPolicy;
import com.haydeproductions.mirror.config.MirrorMode;
import com.haydeproductions.mirror.config.TransferMode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

public record MirrorDefinition(
        String id,
        Path path,
        MirrorMode mode,
        TransferMode transferMode,
        ConflictPolicy conflictPolicy,
        Duration debounce,
        int maxTransferAttempts,
        boolean persistentState
) {
    private static final Pattern ID_PATTERN = Pattern.compile(
            "[A-Za-z][A-Za-z0-9._-]{0,63}"
    );

    public MirrorDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(transferMode, "transferMode");
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        Objects.requireNonNull(debounce, "debounce");

        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Mirror id must match " + ID_PATTERN.pattern() + ": " + id
            );
        }
        if (path.isAbsolute() || path.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Mirror path must be a non-empty relative path: " + path
            );
        }

        path = path.normalize();
        if (path.startsWith("..")) {
            throw new IllegalArgumentException(
                    "Mirror path escapes the mirror root: " + path
            );
        }
        if (debounce.isNegative()) {
            throw new IllegalArgumentException("Mirror debounce cannot be negative");
        }
        if (maxTransferAttempts < 1) {
            throw new IllegalArgumentException(
                    "Mirror maxTransferAttempts must be at least 1"
            );
        }
        if (transferMode == TransferMode.MOVE && mode != MirrorMode.ONE_TIME) {
            throw new IllegalArgumentException(
                    "Mirror MOVE transfer mode is only valid with ONE_TIME mode"
            );
        }
    }
}
