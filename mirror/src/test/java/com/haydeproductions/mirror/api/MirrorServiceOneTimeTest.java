package com.haydeproductions.mirror.api;

import com.haydeproductions.mirror.config.MirrorConfig;
import com.haydeproductions.mirror.config.TransferMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.haydeproductions.mirror.support.TestSupport.awaitContent;
import static com.haydeproductions.mirror.support.TestSupport.write;
import static org.junit.jupiter.api.Assertions.*;

class MirrorServiceOneTimeTest {
    @TempDir Path temp;

    @Test
    void addTargetImmediatelyCopiesExistingSource() throws Exception {
        Roots roots = roots();
        Path sourceFile = write(roots.source, "a/file.txt", "one");
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.addTarget(sourceFile);
            assertEquals("one", Files.readString(roots.mirror.resolve("a/file.txt")));
            assertTrue(service.isTarget(sourceFile));
            assertEquals(java.util.Set.of(sourceFile.toAbsolutePath().normalize()), service.targets());
        }
    }

    @Test
    void missingTargetCanBeRegisteredAndCopiedWhenCreatedLater() throws Exception {
        Roots roots = roots();
        Path sourceFile = roots.source.resolve("later/file.txt");
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.addTarget(sourceFile);
            assertFalse(Files.exists(roots.mirror.resolve("later/file.txt")));
            service.start();
            write(roots.source, "later/file.txt", "created");
            awaitContent(roots.mirror.resolve("later/file.txt"), "created");
        }
    }

    @Test
    void sourceModificationCopiesNewVersion() throws Exception {
        Roots roots = roots();
        Path sourceFile = write(roots.source, "file.txt", "v1");
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.addTarget(sourceFile);
            service.start();
            Files.writeString(sourceFile, "v2");
            awaitContent(roots.mirror.resolve("file.txt"), "v2");
        }
    }

    @Test
    void mirrorSideModificationIsIgnoredUntilSourceChanges() throws Exception {
        Roots roots = roots();
        Path sourceFile = write(roots.source, "file.txt", "source-v1");
        Path mirrorFile = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.addTarget(sourceFile);
            service.start();

            Files.writeString(mirrorFile, "manual-mirror-change");
            Thread.sleep(400);
            assertEquals("manual-mirror-change", Files.readString(mirrorFile));

            Files.writeString(sourceFile, "source-v2");
            awaitContent(mirrorFile, "source-v2");
        }
    }

    @Test
    void mirrorDeletionIsIgnoredButLaterSourceChangeRecreatesIt() throws Exception {
        Roots roots = roots();
        Path sourceFile = write(roots.source, "file.txt", "v1");
        Path mirrorFile = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.addTarget(sourceFile);
            service.start();

            Files.delete(mirrorFile);
            Thread.sleep(400);
            assertFalse(Files.exists(mirrorFile));

            Files.writeString(sourceFile, "v2");
            awaitContent(mirrorFile, "v2");
        }
    }

    @Test
    void sourceDeletionDoesNotDeleteMirror() throws Exception {
        Roots roots = roots();
        Path sourceFile = write(roots.source, "file.txt", "payload");
        Path mirrorFile = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.addTarget(sourceFile);
            service.start();
            Files.delete(sourceFile);
            Thread.sleep(400);
            assertEquals("payload", Files.readString(mirrorFile));
        }
    }

    @Test
    void moveTransfersThenRemovesSource() throws Exception {
        Roots roots = roots();
        Path sourceFile = write(roots.source, "file.txt", "payload");
        MirrorConfig config = MirrorConfig.builder(roots.source, roots.mirror)
                .transferMode(TransferMode.MOVE)
                .debounce(Duration.ofMillis(25))
                .build();
        try (MirrorService service = MirrorServices.create(config)) {
            service.addTarget(sourceFile);
            assertFalse(Files.exists(sourceFile));
            assertEquals("payload", Files.readString(roots.mirror.resolve("file.txt")));
        }
    }

    @Test
    void removeTargetStopsFutureMirroringAndDoesNotDeleteCopies() throws Exception {
        Roots roots = roots();
        Path sourceFile = write(roots.source, "file.txt", "v1");
        Path mirrorFile = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.addTarget(sourceFile);
            service.start();
            service.removeTarget(sourceFile);
            assertFalse(service.isTarget(sourceFile));
            Files.writeString(sourceFile, "v2");
            Thread.sleep(400);
            assertEquals("v1", Files.readString(mirrorFile));
        }
    }

    @Test
    void addingSameTargetAgainIsIdempotentAndDoesNotOverwriteMirrorChanges() throws Exception {
        Roots roots = roots();
        Path sourceFile = write(roots.source, "file.txt", "source");
        Path mirrorFile = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.addTarget(sourceFile);
            Files.writeString(mirrorFile, "manual");
            service.addTarget(sourceFile);
            assertEquals("manual", Files.readString(mirrorFile));
            assertEquals(1, service.targets().size());
        }
    }

    @Test
    void startAndStopAreIdempotent() throws Exception {
        Roots roots = roots();
        try (MirrorService service = MirrorServices.create(config(roots))) {
            service.start();
            service.start();
            assertTrue(service.isRunning());
            service.stop();
            service.stop();
            assertFalse(service.isRunning());
        }
    }

    @Test
    void watcherErrorsCanBeCollectedWithoutKillingService() throws Exception {
        Roots roots = roots();
        List<Throwable> errors = new ArrayList<>();
        Path sourceFile = write(roots.source, "file.txt", "v1");
        try (MirrorService service = MirrorServices.create(config(roots), errors::add)) {
            service.addTarget(sourceFile);
            service.start();
            assertTrue(service.isRunning());
            service.stop();
            assertFalse(service.isRunning());
            assertTrue(errors.isEmpty());
        }
    }

    private MirrorConfig config(Roots roots) {
        return MirrorConfig.builder(roots.source, roots.mirror)
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
