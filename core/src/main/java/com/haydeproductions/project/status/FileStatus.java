package com.haydeproductions.project.status;

import com.haydeproductions.project.state.FileState;

import java.util.Objects;
import java.util.Optional;

public record FileStatus(
        String path,
        Optional<FileState> state
) {
    public FileStatus {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        state = Objects.requireNonNull(state);
    }

    public static FileStatus tracked(String path, FileState state) {
        return new FileStatus(
                path,
                Optional.of(Objects.requireNonNull(state))
        );
    }

    public static FileStatus untracked(String path) {
        return new FileStatus(path, Optional.empty());
    }

    public boolean isTracked() {
        return state.isPresent();
    }

    public String getStateName() {
        return state
                .map(Enum::name)
                .orElse("UNTRACKED");
    }

    public boolean isSafe() {
        return state.orElse(null) == FileState.SAFE;
    }
}
