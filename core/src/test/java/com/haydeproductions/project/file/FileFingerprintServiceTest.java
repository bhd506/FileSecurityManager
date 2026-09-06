package com.haydeproductions.project.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileFingerprintServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void fingerprintsKnownContent() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello");

        FileFingerprint fingerprint =
                new FileFingerprintService().fingerprint(file);

        assertEquals(5, fingerprint.size());
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                fingerprint.sha256()
        );
    }

    @Test
    void emptyFileHasCorrectFingerprint() throws IOException {
        Path file = tempDir.resolve("empty.bin");
        Files.createFile(file);

        FileFingerprint fingerprint =
                new FileFingerprintService().fingerprint(file);

        assertEquals(0, fingerprint.size());
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                fingerprint.sha256()
        );
    }

    @Test
    void normalizesPathBeforeReading() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "data");

        Path equivalent = tempDir
                .resolve("folder")
                .resolve("..")
                .resolve("file.txt");

        assertEquals(
                new FileFingerprintService().fingerprint(file),
                new FileFingerprintService().fingerprint(equivalent)
        );
    }

    @Test
    void missingFileThrowsNoSuchFileException() {
        assertThrows(
                NoSuchFileException.class,
                () -> new FileFingerprintService().fingerprint(
                        tempDir.resolve("missing.txt")
                )
        );
    }

    @Test
    void rejectsNullPath() {
        assertThrows(
                NullPointerException.class,
                () -> new FileFingerprintService().fingerprint(null)
        );
    }
}
