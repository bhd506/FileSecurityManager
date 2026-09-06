package com.haydeproductions.mirror.api;

import com.haydeproductions.mirror.config.MirrorConfig;
import com.haydeproductions.mirror.config.MirrorMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static com.haydeproductions.mirror.support.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MirrorServiceOneWayTest {
    @TempDir Path temp;

    @Test
    void sourceToMirrorCopiesChangesAndPropagatesDeletion() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "x/file.txt", "v1");
        Path mirror = roots.mirror.resolve("x/file.txt");
        try (MirrorService service = MirrorServices.create(config(roots, MirrorMode.SOURCE_TO_MIRROR))) {
            service.addTarget(source);
            service.start();
            assertEquals("v1", Files.readString(mirror));
            Files.writeString(source, "v2");
            awaitContent(mirror, "v2");
            Files.delete(source);
            awaitMissing(mirror);
        }
    }

    @Test
    void sourceToMirrorRestoresMirrorSideModificationAndDeletion() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "file.txt", "authoritative");
        Path mirror = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots, MirrorMode.SOURCE_TO_MIRROR))) {
            service.addTarget(source);
            service.start();
            Files.writeString(mirror, "bad-change");
            awaitContent(mirror, "authoritative");
            Files.delete(mirror);
            awaitContent(mirror, "authoritative");
        }
    }

    @Test
    void mirrorToSourceUsesMirrorAsAuthority() throws Exception {
        Roots roots = roots();
        Path mirror = write(roots.mirror, "file.txt", "mirror-v1");
        Path source = roots.source.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots, MirrorMode.MIRROR_TO_SOURCE))) {
            service.addTarget(source);
            assertEquals("mirror-v1", Files.readString(source));
            service.start();
            Files.writeString(mirror, "mirror-v2");
            awaitContent(source, "mirror-v2");
            Files.writeString(source, "manual-source");
            awaitContent(source, "mirror-v2");
            Files.delete(mirror);
            awaitMissing(source);
        }
    }

    private MirrorConfig config(Roots roots, MirrorMode mode) {
        return MirrorConfig.builder(roots.source, roots.mirror)
                .mode(mode)
                .debounce(Duration.ofMillis(25))
                .build();
    }

    private Roots roots() throws Exception {
        Path source = temp.resolve("source");
        Path mirror = temp.resolve("mirror");
        Files.createDirectories(source);
        Files.createDirectories(mirror);
        return new Roots(source, mirror);
    }

    private record Roots(Path source, Path mirror) {}
}
