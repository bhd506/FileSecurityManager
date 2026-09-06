package com.haydeproductions.project.rule;

import java.nio.file.Path;
import java.util.Objects;

public final class FileContext {

    private final Path path;

    public FileContext(Path path) {
        this.path = Objects.requireNonNull(path);
    }

    public Path getPath() {
        return path;
    }
}