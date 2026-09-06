package com.haydeproductions.mirror.core;

import com.haydeproductions.mirror.exception.InvalidMirrorTargetException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class PathSafety {
    private PathSafety() {
    }

    public static void validateRoot(Path root, boolean create) throws IOException {
        if (create) {
            Files.createDirectories(root);
        }
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Root does not exist: " + root);
        }
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Symbolic-link roots are not supported: " + root);
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Root is not a directory: " + root);
        }
    }

    public static void ensureNoSymlinkTraversal(Path root, Path target) throws IOException {
        Path normalRoot = root.toAbsolutePath().normalize();
        Path normalTarget = target.toAbsolutePath().normalize();
        if (!normalTarget.startsWith(normalRoot)) {
            throw new InvalidMirrorTargetException("Path escapes managed root: " + target);
        }

        Path current = normalRoot;
        Path relative = normalRoot.relativize(normalTarget);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new InvalidMirrorTargetException("Symbolic-link traversal is not supported: " + current);
            }
        }
    }

    public static void ensureRootsDoNotOverlap(Path sourceRoot, Path mirrorRoot) throws IOException {
        Path realSource = sourceRoot.toRealPath();
        Path realMirror = mirrorRoot.toRealPath();
        if (realSource.equals(realMirror)
                || realSource.startsWith(realMirror)
                || realMirror.startsWith(realSource)) {
            throw new IOException("Source and mirror roots resolve to overlapping locations");
        }
    }
}
