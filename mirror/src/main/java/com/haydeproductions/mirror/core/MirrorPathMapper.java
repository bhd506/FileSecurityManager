package com.haydeproductions.mirror.core;

import com.haydeproductions.mirror.exception.InvalidMirrorTargetException;

import java.nio.file.Path;

public final class MirrorPathMapper {
    private final Path sourceRoot;
    private final Path mirrorRoot;

    public MirrorPathMapper(Path sourceRoot, Path mirrorRoot) {
        this.sourceRoot = sourceRoot.toAbsolutePath().normalize();
        this.mirrorRoot = mirrorRoot.toAbsolutePath().normalize();
    }

    public Path toRelativeSource(Path sourcePath) throws InvalidMirrorTargetException {
        Path normalised = sourcePath.toAbsolutePath().normalize();
        if (!normalised.startsWith(sourceRoot)) {
            throw new InvalidMirrorTargetException("Target is outside source root: " + sourcePath);
        }
        Path relative = sourceRoot.relativize(normalised).normalize();
        validateRelative(relative);
        return relative;
    }

    public Path source(Path relative) throws InvalidMirrorTargetException {
        validateRelative(relative);
        Path resolved = sourceRoot.resolve(relative).normalize();
        if (!resolved.startsWith(sourceRoot)) {
            throw new InvalidMirrorTargetException("Relative path escapes source root: " + relative);
        }
        return resolved;
    }

    public Path mirror(Path relative) throws InvalidMirrorTargetException {
        validateRelative(relative);
        Path resolved = mirrorRoot.resolve(relative).normalize();
        if (!resolved.startsWith(mirrorRoot)) {
            throw new InvalidMirrorTargetException("Relative path escapes mirror root: " + relative);
        }
        return resolved;
    }

    public Path relativeFromMirror(Path mirrorPath) throws InvalidMirrorTargetException {
        Path normalised = mirrorPath.toAbsolutePath().normalize();
        if (!normalised.startsWith(mirrorRoot)) {
            throw new InvalidMirrorTargetException("Path is outside mirror root: " + mirrorPath);
        }
        Path relative = mirrorRoot.relativize(normalised).normalize();
        validateRelative(relative);
        return relative;
    }

    private static void validateRelative(Path relative) throws InvalidMirrorTargetException {
        if (relative == null || relative.isAbsolute() || relative.toString().isEmpty()) {
            throw new InvalidMirrorTargetException("Mirror targets must identify a file below the root");
        }
        if (relative.startsWith("..")) {
            throw new InvalidMirrorTargetException("Relative path escapes managed root: " + relative);
        }
    }
}
