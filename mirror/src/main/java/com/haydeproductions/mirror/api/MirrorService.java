package com.haydeproductions.mirror.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface MirrorService extends AutoCloseable {
    void addTarget(Path sourceFile) throws IOException;

    void removeTarget(Path sourceFile) throws IOException;

    boolean isTarget(Path sourceFile) throws IOException;

    Set<Path> targets();

    void reconcileTarget(Path sourceFile) throws IOException;

    void reconcileAll() throws IOException;

    void start() throws IOException;

    void stop() throws IOException;

    boolean isRunning();

    @Override
    default void close() throws IOException {
        stop();
    }
}
