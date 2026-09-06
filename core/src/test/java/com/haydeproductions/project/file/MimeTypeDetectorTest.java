package com.haydeproductions.project.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MimeTypeDetectorTest {

    @TempDir
    Path tempDir;

    private final MimeTypeDetector detector = new MimeTypeDetector();

    @Test
    void knownSignatureHasDeterministicMimeType() throws Exception {
        Path file = tempDir.resolve("misleading.txt");
        Files.write(file, new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        });

        assertEquals(
                "image/png",
                detector.detect(file, FileSignature.PNG)
        );
    }
}
