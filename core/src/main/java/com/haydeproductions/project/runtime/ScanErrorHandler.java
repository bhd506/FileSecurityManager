package com.haydeproductions.project.runtime;

import java.nio.file.Path;

@FunctionalInterface
public interface ScanErrorHandler {

    void onScanError(Path path, Exception exception);
}
