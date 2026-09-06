package com.haydeproductions.project.rule;

import com.haydeproductions.project.file.FileSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileContextTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsSuppliedPath() {
        Path path = Path.of("file.txt");

        FileContext context = new FileContext(path);

        assertSame(path, context.getPath());
    }

    @Test
    void exposesAbsoluteNormalizedPathWithoutChangingSuppliedPath() {
        Path path = tempDir.resolve("folder/../file.txt");
        FileContext context = new FileContext(path);

        assertSame(path, context.getPath());
        assertEquals(
                path.toAbsolutePath().normalize(),
                context.getNormalizedPath()
        );
    }

    @Test
    void returnsFileName() {
        FileContext context = new FileContext(
                tempDir.resolve("folder/example.txt")
        );

        assertEquals("example.txt", context.getFileName());
    }

    @Test
    void returnsAllExtensionSuffixes() {
        FileContext context = new FileContext(
                tempDir.resolve("archive.tar.gz")
        );

        assertEquals(
                List.of("tar.gz", "gz"),
                context.getExtensions()
        );
    }

    @Test
    void extensionSuffixesPreserveOriginalCase() {
        FileContext context = new FileContext(
                tempDir.resolve("ARCHIVE.TAR.GZ")
        );

        assertEquals(
                List.of("TAR.GZ", "GZ"),
                context.getExtensions()
        );
    }

    @Test
    void hiddenFileWithoutAdditionalDotHasNoExtension() {
        FileContext context = new FileContext(
                tempDir.resolve(".bashrc")
        );

        assertTrue(context.getExtensions().isEmpty());
    }

    @Test
    void extensionListIsImmutable() {
        FileContext context = new FileContext(
                tempDir.resolve("file.txt")
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> context.getExtensions().add("exe")
        );
    }

    @Test
    void returnsFileSize() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "hello");

        assertEquals(
                5,
                new FileContext(file).getSize()
        );
    }

    @Test
    void returnsLastModifiedTime() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "hello");
        FileTime expected = FileTime.from(
                Instant.parse("2025-01-02T03:04:05Z")
        );
        Files.setLastModifiedTime(file, expected);

        assertEquals(
                expected,
                new FileContext(file).getLastModifiedTime()
        );
    }

    @Test
    void returnsSha256() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "hello");

        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e"
                        + "1b161e5c1fa7425e73043362938b9824",
                new FileContext(file).getSha256()
        );
    }

    @Test
    void detectsFileSignature() throws IOException {
        Path file = tempDir.resolve("not-really.png");
        Files.write(
                file,
                new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );

        assertEquals(
                FileSignature.PNG,
                new FileContext(file).getFileSignature()
        );
    }

    @Test
    void mimeTypeUsesDetectedSignatureRatherThanExtension() throws IOException {
        Path file = tempDir.resolve("picture.txt");
        Files.write(
                file,
                new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );

        assertEquals(
                "image/png",
                new FileContext(file).getMimeType()
        );
    }

    @Test
    void metadataIsCachedWithinContext() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "12345");

        FileContext context = new FileContext(file);
        assertEquals(5, context.getSize());

        Files.writeString(file, "123456789");

        assertEquals(5, context.getSize());
    }

    @Test
    void hashIsCachedWithinContext() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "hello");

        FileContext context = new FileContext(file);
        String first = context.getSha256();

        Files.writeString(file, "different");

        assertEquals(first, context.getSha256());
    }

    @Test
    void signatureIsCachedWithinContext() throws IOException {
        Path file = tempDir.resolve("file.bin");
        Files.write(
                file,
                new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );

        FileContext context = new FileContext(file);
        assertEquals(FileSignature.PNG, context.getFileSignature());

        Files.writeString(file, "plain text");

        assertEquals(FileSignature.PNG, context.getFileSignature());
    }

    @Test
    void mimeTypeIsCachedWithinContext() throws IOException {
        Path file = tempDir.resolve("file.bin");
        Files.write(
                file,
                new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );

        FileContext context = new FileContext(file);
        assertEquals("image/png", context.getMimeType());

        Files.writeString(file, "plain text");

        assertEquals("image/png", context.getMimeType());
    }

    @Test
    void rejectsNullPath() {
        assertThrows(
                NullPointerException.class,
                () -> new FileContext(null)
        );
    }
}
