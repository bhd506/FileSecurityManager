package com.haydeproductions.project.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileSignatureDetectorTest {

    @TempDir
    Path tempDir;

    private final FileSignatureDetector detector =
            new FileSignatureDetector();

    @Test
    void detectsPng() throws Exception {
        assertSignature(
                FileSignature.PNG,
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        );
    }

    @Test
    void detectsJpeg() throws Exception {
        assertSignature(FileSignature.JPEG, bytes(0xFF, 0xD8, 0xFF, 0x00));
    }

    @Test
    void detectsGif() throws Exception {
        assertSignature(FileSignature.GIF, "GIF89a".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void detectsPdf() throws Exception {
        assertSignature(FileSignature.PDF, "%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void detectsZip() throws Exception {
        assertSignature(FileSignature.ZIP, bytes(0x50, 0x4B, 0x03, 0x04));
    }

    @Test
    void detectsGzip() throws Exception {
        assertSignature(FileSignature.GZIP, bytes(0x1F, 0x8B, 0x08));
    }

    @Test
    void detectsPortableExecutable() throws Exception {
        assertSignature(FileSignature.PE, "MZ".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void detectsElf() throws Exception {
        assertSignature(FileSignature.ELF, bytes(0x7F, 0x45, 0x4C, 0x46));
    }

    @Test
    void detectsMachO() throws Exception {
        assertSignature(FileSignature.MACH_O, bytes(0xFE, 0xED, 0xFA, 0xCF));
    }

    @Test
    void detectsJavaClass() throws Exception {
        assertSignature(FileSignature.JAVA_CLASS, bytes(0xCA, 0xFE, 0xBA, 0xBE));
    }

    @Test
    void detectsTar() throws Exception {
        byte[] tar = new byte[512];
        byte[] marker = "ustar\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, tar, 257, marker.length);

        assertSignature(FileSignature.TAR, tar);
    }

    @Test
    void unknownDataReturnsUnknown() throws Exception {
        assertSignature(
                FileSignature.UNKNOWN,
                "ordinary text".getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    void emptyFileReturnsUnknown() throws Exception {
        assertSignature(FileSignature.UNKNOWN, new byte[0]);
    }

    private void assertSignature(
            FileSignature expected,
            byte[] content
    ) throws Exception {
        Path file = tempDir.resolve("file-" + java.util.UUID.randomUUID());
        Files.write(file, content);

        assertEquals(expected, detector.detect(file));
    }

    private byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
