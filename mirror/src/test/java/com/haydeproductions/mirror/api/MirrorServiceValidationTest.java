package com.haydeproductions.mirror.api;

import com.haydeproductions.mirror.config.MirrorConfig;
import com.haydeproductions.mirror.exception.InvalidMirrorTargetException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MirrorServiceValidationTest {
    @TempDir Path temp;

    @Test
    void sourceRootMustExist() {
        MirrorConfig config = MirrorConfig.builder(temp.resolve("missing"), temp.resolve("mirror")).build();
        assertThrows(java.io.IOException.class, () -> MirrorServices.create(config));
    }

    @Test
    void mirrorRootIsCreated() throws Exception {
        Path source = temp.resolve("source");
        Path mirror = temp.resolve("mirror");
        Files.createDirectories(source);
        try (MirrorService ignored = MirrorServices.create(MirrorConfig.builder(source, mirror).build())) {
            assertTrue(Files.isDirectory(mirror));
        }
    }

    @Test
    void targetOutsideSourceRootIsRejected() throws Exception {
        Path source = temp.resolve("source");
        Path mirror = temp.resolve("mirror");
        Files.createDirectories(source);
        try (MirrorService service = MirrorServices.create(MirrorConfig.builder(source, mirror).build())) {
            assertThrows(InvalidMirrorTargetException.class, () -> service.addTarget(temp.resolve("outside.txt")));
        }
    }

    @Test
    void directoryTargetIsRejected() throws Exception {
        Path source = temp.resolve("source");
        Path mirror = temp.resolve("mirror");
        Files.createDirectories(source.resolve("folder"));
        try (MirrorService service = MirrorServices.create(MirrorConfig.builder(source, mirror).build())) {
            assertThrows(InvalidMirrorTargetException.class, () -> service.addTarget(source.resolve("folder")));
        }
    }

    @Test
    void symlinkTargetIsRejectedWhenSupported() throws Exception {
        Path source = temp.resolve("source");
        Path mirror = temp.resolve("mirror");
        Files.createDirectories(source);
        Path real = source.resolve("real.txt");
        Files.writeString(real, "x");
        Path link = source.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, real.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException e) {
            return;
        }
        try (MirrorService service = MirrorServices.create(MirrorConfig.builder(source, mirror).build())) {
            assertThrows(InvalidMirrorTargetException.class, () -> service.addTarget(link));
        }
    }
}
