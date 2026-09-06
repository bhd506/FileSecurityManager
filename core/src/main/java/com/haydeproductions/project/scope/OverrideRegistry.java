package com.haydeproductions.project.scope;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public final class OverrideRegistry {

    private final Set<Path> overriddenPaths;

    public OverrideRegistry(Set<Path> overriddenPaths) {
        Objects.requireNonNull(overriddenPaths);

        this.overriddenPaths = overriddenPaths.stream()
                .map(Objects::requireNonNull)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean isOverride(Path path) {
        Path normalized = Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();

        return overriddenPaths.contains(normalized);
    }

    public void logOverrides(){
        for (Path path : this.overriddenPaths){
            System.out.println(path);
        }
    }
}
