package com.haydeproductions.project.scan;

import java.nio.file.Path;
import java.util.Objects;

public final class FileChangedDuringScanException extends FileScanException {

    private final Path path;

    public FileChangedDuringScanException(Path path) {
        super("File changed while it was being scanned: " + normalize(path));
        this.path = normalize(path);
    }

    public Path getPath() {
        return path;
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }
}
