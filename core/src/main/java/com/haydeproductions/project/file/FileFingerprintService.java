package com.haydeproductions.project.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class FileFingerprintService {

    public FileFingerprint fingerprint(Path path) throws IOException {
        Path normalized = Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();

        MessageDigest digest = sha256Digest();
        long size = 0;

        try (InputStream input = Files.newInputStream(
                normalized,
                StandardOpenOption.READ
        )) {
            byte[] buffer = new byte[8192];
            int read;

            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                size += read;
            }
        }

        return new FileFingerprint(
                size,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable in this Java runtime",
                    exception
            );
        }
    }
}
