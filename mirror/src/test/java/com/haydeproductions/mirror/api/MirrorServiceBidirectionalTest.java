package com.haydeproductions.mirror.api;

import com.haydeproductions.mirror.config.ConflictPolicy;
import com.haydeproductions.mirror.config.MirrorConfig;
import com.haydeproductions.mirror.config.MirrorMode;
import com.haydeproductions.mirror.exception.MirrorConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static com.haydeproductions.mirror.support.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MirrorServiceBidirectionalTest {
    @TempDir Path temp;

    @Test
    void sourceOnlyInitialStateCopiesToMirror() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "file.txt", "source");
        try (MirrorService service = MirrorServices.create(config(roots, ConflictPolicy.FAIL, null))) {
            service.addTarget(source);
            assertEquals("source", Files.readString(roots.mirror.resolve("file.txt")));
        }
    }

    @Test
    void mirrorOnlyInitialStateCopiesToSource() throws Exception {
        Roots roots = roots();
        write(roots.mirror, "file.txt", "mirror");
        Path source = roots.source.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots, ConflictPolicy.FAIL, null))) {
            service.addTarget(source);
            assertEquals("mirror", Files.readString(source));
        }
    }

    @Test
    void differingInitialFilesConflictByDefault() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "file.txt", "source");
        write(roots.mirror, "file.txt", "mirror");
        try (MirrorService service = MirrorServices.create(config(roots, ConflictPolicy.FAIL, null))) {
            assertThrows(MirrorConflictException.class, () -> service.addTarget(source));
            assertFalse(service.isTarget(source));
            assertEquals("source", Files.readString(source));
            assertEquals("mirror", Files.readString(roots.mirror.resolve("file.txt")));
        }
    }

    @Test
    void sourceWinsInitialConflictWhenConfigured() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "file.txt", "source");
        Path mirror = write(roots.mirror, "file.txt", "mirror");
        try (MirrorService service = MirrorServices.create(config(roots, ConflictPolicy.SOURCE_WINS, null))) {
            service.addTarget(source);
            assertEquals("source", Files.readString(mirror));
        }
    }

    @Test
    void mirrorWinsInitialConflictWhenConfigured() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "file.txt", "source");
        write(roots.mirror, "file.txt", "mirror");
        try (MirrorService service = MirrorServices.create(config(roots, ConflictPolicy.MIRROR_WINS, null))) {
            service.addTarget(source);
            assertEquals("mirror", Files.readString(source));
        }
    }

    @Test
    void changesPropagateInBothDirectionsWithoutFeedbackLoop() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "file.txt", "v1");
        Path mirror = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots, ConflictPolicy.FAIL, null))) {
            service.addTarget(source);
            service.start();

            Files.writeString(source, "source-v2");
            awaitContent(mirror, "source-v2");

            Files.writeString(mirror, "mirror-v3");
            awaitContent(source, "mirror-v3");

            Thread.sleep(300);
            assertEquals("mirror-v3", Files.readString(source));
            assertEquals("mirror-v3", Files.readString(mirror));
        }
    }

    @Test
    void deletionPropagatesBothDirections() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "file.txt", "v1");
        Path mirror = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots, ConflictPolicy.FAIL, null))) {
            service.addTarget(source);
            service.start();
            Files.delete(source);
            awaitMissing(mirror);

            Files.writeString(mirror, "reborn");
            awaitContent(source, "reborn");
            Files.delete(mirror);
            awaitMissing(source);
        }
    }

    @Test
    void simultaneousIndependentChangesProduceConflictAndPreserveBoth() throws Exception {
        Roots roots = roots();
        Path source = write(roots.source, "file.txt", "base");
        Path mirror = roots.mirror.resolve("file.txt");
        try (MirrorService service = MirrorServices.create(config(roots, ConflictPolicy.FAIL, null))) {
            service.addTarget(source);
            Files.writeString(source, "source-change");
            Files.writeString(mirror, "mirror-change");
            assertThrows(MirrorConflictException.class, () -> service.reconcileTarget(source));
            assertEquals("source-change", Files.readString(source));
            assertEquals("mirror-change", Files.readString(mirror));
        }
    }

    @Test
    void persistentStateAllowsRestartToIdentifyWhichSideChanged() throws Exception {
        Roots roots = roots();
        Path stateFile = temp.resolve("state/sync.properties");
        Path source = write(roots.source, "file.txt", "base");
        Path mirror = roots.mirror.resolve("file.txt");

        try (MirrorService first = MirrorServices.create(config(roots, ConflictPolicy.FAIL, stateFile))) {
            first.addTarget(source);
        }

        Files.writeString(source, "changed-while-stopped");

        try (MirrorService second = MirrorServices.create(config(roots, ConflictPolicy.FAIL, stateFile))) {
            second.addTarget(source);
            assertEquals("changed-while-stopped", Files.readString(mirror));
        }
    }

    @Test
    void persistentStateDetectsOfflineTwoSidedConflict() throws Exception {
        Roots roots = roots();
        Path stateFile = temp.resolve("state/sync.properties");
        Path source = write(roots.source, "file.txt", "base");
        Path mirror = roots.mirror.resolve("file.txt");

        try (MirrorService first = MirrorServices.create(config(roots, ConflictPolicy.FAIL, stateFile))) {
            first.addTarget(source);
        }

        Files.writeString(source, "source-offline-change");
        Files.writeString(mirror, "mirror-offline-change");

        try (MirrorService second = MirrorServices.create(config(roots, ConflictPolicy.FAIL, stateFile))) {
            assertThrows(MirrorConflictException.class, () -> second.addTarget(source));
            assertEquals("source-offline-change", Files.readString(source));
            assertEquals("mirror-offline-change", Files.readString(mirror));
        }
    }

    private MirrorConfig config(Roots roots, ConflictPolicy policy, Path stateFile) {
        MirrorConfig.Builder builder = MirrorConfig.builder(roots.source, roots.mirror)
                .mode(MirrorMode.BIDIRECTIONAL)
                .conflictPolicy(policy)
                .debounce(Duration.ofMillis(25));
        if (stateFile != null) {
            builder.stateFile(stateFile);
        }
        return builder.build();
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
