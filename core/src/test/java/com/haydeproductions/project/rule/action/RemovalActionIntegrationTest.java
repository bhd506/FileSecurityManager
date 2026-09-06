package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.log.DeletionRecord;
import com.haydeproductions.project.quarantine.QuarantineService;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.condition.TrueCondition;
import com.haydeproductions.project.scan.FileScanner;
import com.haydeproductions.project.scan.RuleSetIndex;
import com.haydeproductions.project.scan.ScanCoordinator;
import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;
import com.haydeproductions.project.state.FileStateRegistry;
import com.haydeproductions.project.testing.RecordingLogHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RemovalActionIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void flagQuarantineDeleteExecutesInPhaseOrderAcrossOneScan() throws Exception {
        Path root = tempDir.resolve("source");
        Path file = root.resolve("file.txt");
        Files.createDirectories(root);
        Files.writeString(file, "payload");

        Path quarantineRoot = tempDir.resolve("quarantine");
        RecordingLogHandler logs = new RecordingLogHandler();
        FileStateRegistry states = new FileStateRegistry();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(
                        new DeleteAction(logs),
                        new QuarantineAction(
                                new QuarantineService(quarantineRoot),
                                logs
                        ),
                        new FlagAction()
                ),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).rule(rule).build();

        FileScanner scanner = new FileScanner(
                new ScanCoordinator(
                        new RuleSetIndex(List.of(ruleSet))
                ),
                new ActionExecutor(states),
                states
        );

        scanner.scan(file);

        assertFalse(Files.exists(file));
        assertEquals(Optional.empty(), states.getState(file));
        assertEquals(1, logs.deletions().size());

        DeletionRecord deletion = logs.deletions().getFirst();
        assertEquals(file.toAbsolutePath().normalize(), deletion.originalPath());
        assertTrue(deletion.deletedPath().startsWith(quarantineRoot));
        assertFalse(Files.exists(deletion.deletedPath()));
    }

    @Test
    void quarantineWithoutDeleteLeavesQuarantinedStateAndFileOutsideSource() throws Exception {
        Path root = tempDir.resolve("source");
        Path file = root.resolve("file.txt");
        Files.createDirectories(root);
        Files.writeString(file, "payload");

        Path quarantineRoot = tempDir.resolve("quarantine");
        RecordingLogHandler logs = new RecordingLogHandler();
        FileStateRegistry states = new FileStateRegistry();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(new QuarantineAction(
                        new QuarantineService(quarantineRoot),
                        logs
                )),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).rule(rule).build();

        FileScanner scanner = new FileScanner(
                new ScanCoordinator(new RuleSetIndex(List.of(ruleSet))),
                new ActionExecutor(states),
                states
        );

        scanner.scan(file);

        assertFalse(Files.exists(file));
        assertEquals(
                Optional.of(com.haydeproductions.project.state.FileState.QUARANTINED),
                states.getState(file)
        );
        assertTrue(logs.deletions().isEmpty());
        assertEquals(1, logs.entries().size());
    }
}
