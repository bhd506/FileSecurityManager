package com.haydeproductions.project.state;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FileStateRegistry {

    private final ConcurrentMap<Path, FileState> states =
            new ConcurrentHashMap<>();

    public void setState(Path path, FileState state) {
        states.put(
                normalize(path),
                Objects.requireNonNull(state)
        );
    }

    public Optional<FileState> getState(Path path) {
        return Optional.ofNullable(
                states.get(normalize(path))
        );
    }

    public Optional<FileState> remove(Path path) {
        return Optional.ofNullable(
                states.remove(normalize(path))
        );
    }

    public boolean contains(Path path) {
        return states.containsKey(
                normalize(path)
        );
    }

    public Map<Path, FileState> snapshot() {
        return Map.copyOf(states);
    }

    public boolean compareAndSet(
            Path path,
            FileState expected,
            FileState newState
    ) {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(newState);

        return states.replace(
                normalize(path),
                expected,
                newState
        );
    }

    private Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }
}