package com.haydeproductions.mirror.transfer;

import com.haydeproductions.mirror.exception.InvalidMirrorTargetException;
import com.haydeproductions.mirror.model.FileFingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class SnapshotService {
    private final FileHasher hasher;

    public SnapshotService(FileHasher hasher) {
        this.hasher = hasher;
    }

    public FileFingerprint fingerprint(Path file) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return FileFingerprint.absent();
        }
        if (Files.isSymbolicLink(file)) {
            throw new InvalidMirrorTargetException("Symbolic links are not supported: " + file);
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidMirrorTargetException("Mirror targets must be regular files: " + file);
        }

        long size = Files.size(file);
        return FileFingerprint.present(size, hasher.sha256(file));
    }

    public boolean equivalent(Path first, Path second) throws IOException {
        if (!Files.exists(first, LinkOption.NOFOLLOW_LINKS)
                || !Files.exists(second, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (Files.size(first) != Files.size(second)) {
            return false;
        }
        return fingerprint(first).equals(fingerprint(second));
    }
}
