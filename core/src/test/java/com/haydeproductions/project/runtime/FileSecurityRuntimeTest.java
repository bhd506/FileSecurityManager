package com.haydeproductions.project.runtime;

import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.ActionExecutor;
import com.haydeproductions.project.rule.action.FlagAction;
import com.haydeproductions.project.rule.condition.TrueCondition;
import com.haydeproductions.project.scan.FileScanner;
import com.haydeproductions.project.scan.RuleSetIndex;
import com.haydeproductions.project.scan.ScanCoordinator;
import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import com.haydeproductions.project.testing.RecordingLogHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class FileSecurityRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    void startPerformsSynchronousInitialFullScan() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Path file = source.resolve("existing.txt");
        Files.writeString(file, "existing");

        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(),
                registry,
                new RecordingLogHandler()
        )) {
            runtime.start();

            assertEquals(
                    Optional.of(FileState.SAFE),
                    registry.getState(file)
            );
            assertTrue(runtime.isRunning());
        }
    }

    @Test
    void initialScanExecutesConfiguredRulesAndActions() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Path file = source.resolve("existing.txt");
        Files.writeString(file, "existing");

        Rule flagRule = new Rule(
                new TrueCondition(),
                List.of(new FlagAction()),
                List.of()
        );

        RuleSet ruleSet = ruleSet(source, flagRule);
        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(ruleSet),
                registry,
                new RecordingLogHandler()
        )) {
            runtime.start();

            assertEquals(
                    Optional.of(FileState.FLAGGED),
                    registry.getState(file)
            );
        }
    }

    @Test
    void newlyCreatedFileIsAutomaticallyScanned() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);

        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(),
                registry,
                new RecordingLogHandler()
        )) {
            runtime.start();

            Path file = source.resolve("new.txt");
            Files.writeString(file, "new");

            assertEventually(
                    () -> registry.getState(file)
                            .equals(Optional.of(FileState.SAFE)),
                    Duration.ofSeconds(6)
            );
        }
    }

    @Test
    void fileModificationTriggersAnotherScan() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Path file = source.resolve("file.txt");
        Files.writeString(file, "one");

        AtomicInteger evaluations = new AtomicInteger();

        Rule countingRule = new Rule(
                context -> {
                    evaluations.incrementAndGet();
                    return true;
                },
                List.of(),
                List.of()
        );

        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(ruleSet(source, countingRule)),
                registry,
                new RecordingLogHandler()
        )) {
            runtime.start();
            assertEquals(1, evaluations.get());

            Files.writeString(file, "two - changed");

            assertEventually(
                    () -> evaluations.get() >= 2,
                    Duration.ofSeconds(6)
            );

            assertEventually(
                    () -> registry.getState(file)
                            .equals(Optional.of(FileState.SAFE)),
                    Duration.ofSeconds(6)
            );
        }
    }

    @Test
    void newDirectoryWithExistingContentsIsReconciled() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);

        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(),
                registry,
                new RecordingLogHandler()
        )) {
            runtime.start();

            Path nested = source.resolve("new/a/b");
            Files.createDirectories(nested);
            Path file = nested.resolve("file.txt");
            Files.writeString(file, "data");

            assertEventually(
                    () -> registry.getState(file)
                            .equals(Optional.of(FileState.SAFE)),
                    Duration.ofSeconds(6)
            );
        }
    }

    @Test
    void externalFileDeletionRemovesActiveStateWithoutRescanning()
            throws Exception {

        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Path file = source.resolve("file.txt");
        Files.writeString(file, "data");

        AtomicInteger evaluations = new AtomicInteger();
        Rule countingRule = new Rule(
                context -> {
                    evaluations.incrementAndGet();
                    return true;
                },
                List.of(),
                List.of()
        );

        RecordingLogHandler log = new RecordingLogHandler();
        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(ruleSet(source, countingRule)),
                registry,
                log
        )) {
            runtime.start();
            assertEquals(1, evaluations.get());

            Files.delete(file);

            assertEventually(
                    () -> registry.getState(file).isEmpty(),
                    Duration.ofSeconds(6)
            );

            assertEventually(
                    () -> log.entries().stream().anyMatch(
                            entry -> entry.type().name()
                                    .equals("SOURCE_DELETE_OBSERVED")
                    ),
                    Duration.ofSeconds(6)
            );

            Thread.sleep(350L);
            assertEquals(1, evaluations.get());
        }
    }

    @Test
    void sourceDeletionDoesNotDiscardQuarantinedState() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Path file = source.resolve("file.txt");
        Files.writeString(file, "data");

        RecordingLogHandler log = new RecordingLogHandler();
        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(),
                registry,
                log
        )) {
            runtime.start();

            registry.setState(file, FileState.QUARANTINED);
            Files.delete(file);

            assertEventually(
                    () -> log.entries().stream().anyMatch(
                            entry -> entry.type().name()
                                    .equals("SOURCE_DELETE_OBSERVED")
                    ),
                    Duration.ofSeconds(6)
            );

            assertEquals(
                    Optional.of(FileState.QUARANTINED),
                    registry.getState(file)
            );
        }
    }

    @Test
    void fullRescanReconcilesStaleStateForMissingSourceFile()
            throws Exception {

        Path source = tempDir.resolve("source");
        Files.createDirectories(source);

        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(),
                registry,
                new RecordingLogHandler()
        )) {
            runtime.start();

            Path stale = source.resolve("missing.txt");
            registry.setState(stale, FileState.SAFE);

            runtime.requestFullRescan();

            assertTrue(runtime.awaitIdle(Duration.ofSeconds(6)));
            assertEquals(Optional.empty(), registry.getState(stale));
        }
    }

    @Test
    void fullRescanReevaluatesEveryExistingFile() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("a.txt"), "a");
        Files.writeString(source.resolve("b.txt"), "b");

        AtomicInteger evaluations = new AtomicInteger();
        Rule countingRule = new Rule(
                context -> {
                    evaluations.incrementAndGet();
                    return true;
                },
                List.of(),
                List.of()
        );

        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(ruleSet(source, countingRule)),
                registry,
                new RecordingLogHandler()
        )) {
            runtime.start();
            assertEquals(2, evaluations.get());

            runtime.requestFullRescan();
            assertTrue(runtime.awaitIdle(Duration.ofSeconds(6)));

            assertEquals(4, evaluations.get());
        }
    }

    @Test
    void stopClosesRuntimeAndPreventsFurtherRescanRequests() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);

        FileStateRegistry registry = new FileStateRegistry();
        FileSecurityRuntime runtime = runtime(
                source,
                List.of(),
                registry,
                new RecordingLogHandler()
        );

        runtime.start();
        runtime.stop();

        assertFalse(runtime.isRunning());
        assertThrows(
                IllegalStateException.class,
                runtime::requestFullRescan
        );
    }

    @Test
    void missingSourceRootFailsAtStart() {
        Path source = tempDir.resolve("missing");
        FileStateRegistry registry = new FileStateRegistry();

        try (FileSecurityRuntime runtime = runtime(
                source,
                List.of(),
                registry,
                new RecordingLogHandler()
        )) {
            assertThrows(
                    java.nio.file.NoSuchFileException.class,
                    runtime::start
            );
        }
    }

    private FileSecurityRuntime runtime(
            Path source,
            List<RuleSet> ruleSets,
            FileStateRegistry registry,
            RecordingLogHandler log
    ) {
        FileScanner scanner = new FileScanner(
                new ScanCoordinator(
                        new RuleSetIndex(ruleSets)
                ),
                new ActionExecutor(registry),
                registry
        );

        return new FileSecurityRuntime(
                source,
                scanner,
                registry,
                log,
                Duration.ofMillis(75),
                2
        );
    }

    private RuleSet ruleSet(Path root, Rule... rules) {
        RuleSet.Builder builder = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        );

        for (Rule rule : rules) {
            builder.rule(rule);
        }

        return builder.build();
    }

    private static void assertEventually(
            BooleanSupplier condition,
            Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }

        assertTrue(condition.getAsBoolean(), "condition did not become true before timeout");
    }
}
