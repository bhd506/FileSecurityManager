package com.haydeproductions.mirror.model;

import java.nio.file.Path;
import java.util.Objects;

public record MirrorEvent(MirrorSide side, Path relativePath) {
    public MirrorEvent {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("MirrorEvent paths must be relative");
        }
        relativePath = relativePath.normalize();
    }
}
