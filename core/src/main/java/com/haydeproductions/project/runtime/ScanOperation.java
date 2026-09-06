package com.haydeproductions.project.runtime;

import java.nio.file.Path;

@FunctionalInterface
public interface ScanOperation {

    void scan(Path path) throws Exception;
}
