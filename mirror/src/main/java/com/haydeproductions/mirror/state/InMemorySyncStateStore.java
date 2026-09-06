package com.haydeproductions.mirror.state;

import com.haydeproductions.mirror.model.FileFingerprint;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemorySyncStateStore implements SyncStateStore {
    private final ConcurrentMap<Path, FileFingerprint> states = new ConcurrentHashMap<>();

    @Override
    public Optional<FileFingerprint> get(Path relative) {
        return Optional.ofNullable(states.get(relative.normalize()));
    }

    @Override
    public void put(Path relative, FileFingerprint fingerprint) {
        states.put(relative.normalize(), fingerprint);
    }

    @Override
    public void remove(Path relative) {
        states.remove(relative.normalize());
    }
}
