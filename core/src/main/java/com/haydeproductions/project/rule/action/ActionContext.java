package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.state.FileStateRegistry;

import java.nio.file.Path;
import java.util.Objects;

public final class ActionContext {

    private final FileContext file;
    private final FileStateRegistry stateRegistry;
    private final Path originalPath;
    private Path currentPath;

    public ActionContext(
            FileContext file,
            FileStateRegistry stateRegistry
    ) {
        this.file = Objects.requireNonNull(file);
        this.stateRegistry = Objects.requireNonNull(stateRegistry);
        this.originalPath = normalize(file.getPath());
        this.currentPath = originalPath;
    }

    public FileContext getFile() {
        return file;
    }

    public FileStateRegistry getStateRegistry() {
        return stateRegistry;
    }

    public Path getOriginalPath() {
        return originalPath;
    }

    public Path getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(Path currentPath) {
        this.currentPath = normalize(currentPath);
    }

    public boolean isAtOriginalPath() {
        return currentPath.equals(originalPath);
    }

    private Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }
}
