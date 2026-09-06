package com.haydeproductions.mirror.exception;

import java.io.IOException;
import java.nio.file.Path;

public final class MirrorConflictException extends IOException {
    private final Path relativePath;

    public MirrorConflictException(Path relativePath) {
        super("Bidirectional mirror conflict for: " + relativePath);
        this.relativePath = relativePath;
    }

    public Path relativePath() {
        return relativePath;
    }
}
