package com.haydeproductions.project.scan;

import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.*;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FileScannerStabilityTest {

    @TempDir
    Path tempDir;

    @Test
    void unchangedRealFileCompletesNormally() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "unchanged");

        FileStateRegistry registry = new FileStateRegistry();
        FileScanner scanner = scanner(List.of(), registry);

        scanner.scan(file);

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(file)
        );
    }

    @Test
    void contentChangeDuringRuleEvaluationInvalidatesScan() throws Exception {
        Path root = tempDir.resolve("data");
        Files.createDirectories(root);
        Path file = root.resolve("file.txt");
        Files.writeString(file, "before");

        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        Rule rule = new Rule(
                context -> {
                    write(context.getPath(), "content changed during scan");
                    return true;
                },
                List.of(recordingAction(actionExecuted)),
                List.of()
        );

        FileStateRegistry registry = new FileStateRegistry();
        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        assertThrows(
                FileChangedDuringScanException.class,
                () -> scanner.scan(file)
        );

        assertFalse(actionExecuted.get());
        assertEquals(
                Optional.of(FileState.UNSCANNED),
                registry.getState(file)
        );
    }

    @Test
    void deletionDuringRuleEvaluationInvalidatesScanAndRemovesState()
            throws Exception {

        Path root = tempDir.resolve("data");
        Files.createDirectories(root);
        Path file = root.resolve("file.txt");
        Files.writeString(file, "before");

        Rule rule = new Rule(
                context -> {
                    delete(context.getPath());
                    return true;
                },
                List.of(new NoOpAction()),
                List.of()
        );

        FileStateRegistry registry = new FileStateRegistry();
        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        assertThrows(
                FileChangedDuringScanException.class,
                () -> scanner.scan(file)
        );

        assertEquals(
                Optional.empty(),
                registry.getState(file)
        );
    }

    @Test
    void actionsRunOnlyAfterFileVersionIsVerified() throws Exception {
        Path root = tempDir.resolve("data");
        Files.createDirectories(root);
        Path file = root.resolve("file.txt");
        Files.writeString(file, "before");

        AtomicBoolean executed = new AtomicBoolean(false);

        Action action = recordingAction(executed);

        Rule rule = new Rule(
                context -> {
                    write(context.getPath(), "after");
                    return true;
                },
                List.of(action),
                List.of()
        );

        FileStateRegistry registry = new FileStateRegistry();
        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        assertThrows(
                FileChangedDuringScanException.class,
                () -> scanner.scan(file)
        );

        assertFalse(executed.get());
    }

    @Test
    void laterStableRescanCanCompleteAfterInvalidation() throws Exception {
        Path root = tempDir.resolve("data");
        Files.createDirectories(root);
        Path file = root.resolve("file.txt");
        Files.writeString(file, "before");

        AtomicBoolean first = new AtomicBoolean(true);

        Rule rule = new Rule(
                context -> {
                    if (first.getAndSet(false)) {
                        write(context.getPath(), "changed once");
                    }
                    return true;
                },
                List.of(),
                List.of()
        );

        FileStateRegistry registry = new FileStateRegistry();
        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        assertThrows(
                FileChangedDuringScanException.class,
                () -> scanner.scan(file)
        );

        scanner.scan(file);

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(file)
        );
    }

    private FileScanner scanner(
            List<RuleSet> ruleSets,
            FileStateRegistry registry
    ) {
        ScanCoordinator coordinator = new ScanCoordinator(
                new RuleSetIndex(ruleSets)
        );

        return new FileScanner(
                coordinator,
                new ActionExecutor(registry),
                registry
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

    private Action recordingAction(AtomicBoolean executed) {
        return new Action() {
            @Override
            public ActionPhase getPhase() {
                return ActionPhase.FLAG;
            }

            @Override
            public void execute(ActionContext context) {
                executed.set(true);
            }
        };
    }

    private void write(Path path, String content)
            throws ConditionEvaluationException {
        try {
            Files.writeString(path, content);
        } catch (IOException exception) {
            throw new ConditionEvaluationException(
                    "test write failed",
                    exception
            );
        }
    }

    private void delete(Path path)
            throws ConditionEvaluationException {
        try {
            Files.delete(path);
        } catch (IOException exception) {
            throw new ConditionEvaluationException(
                    "test delete failed",
                    exception
            );
        }
    }
}
