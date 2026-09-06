package com.haydeproductions.project.scan;

import com.haydeproductions.mirror.config.ConflictPolicy;
import com.haydeproductions.mirror.config.MirrorMode;
import com.haydeproductions.mirror.config.TransferMode;
import com.haydeproductions.project.config.MirrorDefinition;
import com.haydeproductions.project.file.FileFingerprintService;
import com.haydeproductions.project.log.NoOpLogHandler;
import com.haydeproductions.project.mirror.MirrorManager;
import com.haydeproductions.project.rule.action.ActionExecutor;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileScannerMirrorAuthorizationTest {
    @TempDir
    Path tempDir;

    @Test
    void everyNewScanRevokesPreviousMirrorAuthorization() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("source"));
        Path file = sourceRoot.resolve("file.txt");
        Files.writeString(file, "contents");

        MirrorDefinition definition = new MirrorDefinition(
                "backup",
                Path.of("backup"),
                MirrorMode.ONE_TIME,
                TransferMode.COPY,
                ConflictPolicy.FAIL,
                Duration.ofMillis(25),
                3,
                false
        );

        try (MirrorManager mirrors = MirrorManager.create(
                sourceRoot,
                tempDir.resolve("mirrors"),
                tempDir.resolve("states"),
                List.of(definition),
                NoOpLogHandler.INSTANCE
        )) {
            mirrors.allow(file, List.of("backup"));
            assertEquals(java.util.Set.of("backup"), mirrors.activeMirrorIds(file));

            FileStateRegistry states = new FileStateRegistry();
            ActionExecutor executor = new ActionExecutor(states, mirrors);
            FileScanner scanner = new FileScanner(
                    new ScanCoordinator(new RuleSetIndex(List.of())),
                    executor,
                    states,
                    new FileFingerprintService(),
                    mirrors
            );

            scanner.scan(file);

            assertTrue(mirrors.activeMirrorIds(file).isEmpty());
            assertEquals(Optional.of(FileState.SAFE), states.getState(file));
        }
    }
}
