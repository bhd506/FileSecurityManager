package com.haydeproductions.mirror.api;

import com.haydeproductions.mirror.config.ConflictPolicy;
import com.haydeproductions.mirror.config.MirrorConfig;
import com.haydeproductions.mirror.config.MirrorMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static com.haydeproductions.mirror.support.TestSupport.awaitContent;
import static org.junit.jupiter.api.Assertions.*;

class MirrorServiceSuspensionTest {
    @TempDir
    Path temp;

    @Test
    void disabledSourceWatchingRequiresExplicitReconciliation() throws Exception {
        Path sourceRoot = Files.createDirectories(temp.resolve("source"));
        Path mirrorRoot = temp.resolve("mirror");
        Path source = sourceRoot.resolve("file.txt");
        Files.writeString(source, "v1");

        MirrorConfig config = MirrorConfig.builder(sourceRoot, mirrorRoot)
                .mode(MirrorMode.SOURCE_TO_MIRROR)
                .watchSourceChanges(false)
                .debounce(Duration.ofMillis(25))
                .build();

        try (MirrorService service = MirrorServices.create(config)) {
            service.addTarget(source);
            service.start();
            Path mirror = mirrorRoot.resolve("file.txt");
            assertEquals("v1", Files.readString(mirror));

            Files.writeString(source, "v2");
            Thread.sleep(250);
            assertEquals("v1", Files.readString(mirror));

            service.reconcileTarget(source);
            assertEquals("v2", Files.readString(mirror));
        }
    }

    @Test
    void suspendPreservesBidirectionalHistoryForReauthorization() throws Exception {
        Path sourceRoot = Files.createDirectories(temp.resolve("source-bidir"));
        Path mirrorRoot = temp.resolve("mirror-bidir");
        Path source = sourceRoot.resolve("file.txt");
        Files.writeString(source, "base");

        MirrorConfig config = MirrorConfig.builder(sourceRoot, mirrorRoot)
                .mode(MirrorMode.BIDIRECTIONAL)
                .conflictPolicy(ConflictPolicy.FAIL)
                .watchSourceChanges(false)
                .debounce(Duration.ofMillis(25))
                .build();

        try (MirrorService service = MirrorServices.create(config)) {
            service.addTarget(source);
            service.start();
            Path mirror = mirrorRoot.resolve("file.txt");
            assertEquals("base", Files.readString(mirror));

            service.suspendTarget(source);
            assertFalse(service.isTarget(source));

            Files.writeString(source, "new-source-version");
            Thread.sleep(150);
            assertEquals("base", Files.readString(mirror));

            service.addTarget(source);
            assertTrue(service.isTarget(source));
            awaitContent(mirror, "new-source-version");
        }
    }
}
