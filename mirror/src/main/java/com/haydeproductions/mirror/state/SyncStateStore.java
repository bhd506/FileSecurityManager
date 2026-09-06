package com.haydeproductions.mirror.state;

import com.haydeproductions.mirror.model.FileFingerprint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface SyncStateStore {
    Optional<FileFingerprint> get(Path relative) throws IOException;

    void put(Path relative, FileFingerprint fingerprint) throws IOException;

    void remove(Path relative) throws IOException;
}
