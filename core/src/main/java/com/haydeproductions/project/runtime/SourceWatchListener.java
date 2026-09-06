package com.haydeproductions.project.runtime;

import java.nio.file.Path;

public interface SourceWatchListener {

    default void onFileChanged(Path path) {
    }

    default void onFileDeleted(Path path) {
    }

    default void onDirectoryCreated(Path path) {
    }

    default void onDirectoryDeleted(Path path) {
    }

    default void onOverflow(Path directory) {
    }

    default void onWatcherError(Exception exception) {
    }
}
