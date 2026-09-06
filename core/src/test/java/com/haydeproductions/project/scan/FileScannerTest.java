package com.haydeproductions.project.scan;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.action.ActionContext;
import com.haydeproductions.project.rule.action.ActionExecutionException;
import com.haydeproductions.project.rule.action.ActionExecutor;
import com.haydeproductions.project.rule.action.ActionPhase;
import com.haydeproductions.project.rule.action.FlagAction;
import com.haydeproductions.project.rule.action.NoOpAction;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.TrueCondition;
import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FileScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scanWithNoRulesFinishesSafe() throws Exception {
        FileStateRegistry registry = new FileStateRegistry();
        FileScanner scanner = scanner(List.of(), registry);

        Path file = tempDir.resolve("file.txt");

        scanner.scan(file);

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(file)
        );
    }

    @Test
    void fileIsScanningWhileConditionsAreEvaluated() throws Exception {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        Rule rule = new Rule(
                context -> {
                    assertEquals(
                            Optional.of(FileState.SCANNING),
                            registry.getState(context.getPath())
                    );
                    return true;
                },
                List.of(),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        scanner.scan(file);

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(file)
        );
    }

    @Test
    void flagActionLeavesFileFlaggedInsteadOfSafe() throws Exception {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(new FlagAction()),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        scanner.scan(file);

        assertEquals(
                Optional.of(FileState.FLAGGED),
                registry.getState(file)
        );
    }

    @Test
    void scanOnlyConvertsScanningStateToSafe() throws Exception {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        Action quarantineLikeAction = new Action() {
            @Override
            public ActionPhase getPhase() {
                return ActionPhase.QUARANTINE;
            }

            @Override
            public void execute(ActionContext context) {
                context.getStateRegistry().setState(
                        context.getFile().getPath(),
                        FileState.QUARANTINED
                );
            }
        };

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(quarantineLikeAction),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        scanner.scan(file);

        assertEquals(
                Optional.of(FileState.QUARANTINED),
                registry.getState(file)
        );
    }

    @Test
    void actionThatRemovesActiveStateIsNotRecreatedAsSafe() throws Exception {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        Action deleteLikeAction = new Action() {
            @Override
            public ActionPhase getPhase() {
                return ActionPhase.DELETE;
            }

            @Override
            public void execute(ActionContext context) {
                context.getStateRegistry().remove(
                        context.getFile().getPath()
                );
            }
        };

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(deleteLikeAction),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        scanner.scan(file);

        assertEquals(
                Optional.empty(),
                registry.getState(file)
        );
    }

    @Test
    void previousStateIsReplacedByScanningAtStartOfNewScan() throws Exception {
        FileStateRegistry registry = new FileStateRegistry();
        Path file = tempDir.resolve("file.txt");

        registry.setState(file, FileState.FLAGGED);

        FileScanner scanner = scanner(List.of(), registry);

        scanner.scan(file);

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(file)
        );
    }

    @Test
    void conditionFailureSetsErrorAndPropagates() {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        Rule rule = new Rule(
                context -> {
                    throw new ConditionEvaluationException("failure");
                },
                List.of(),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        assertThrows(
                ConditionEvaluationException.class,
                () -> scanner.scan(file)
        );

        assertEquals(
                Optional.of(FileState.ERROR),
                registry.getState(file)
        );
    }

    @Test
    void conditionFailurePreventsPreviouslyScheduledActionsFromExecuting() {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        AtomicBoolean executed = new AtomicBoolean(false);

        Action recordingAction = new Action() {
            @Override
            public ActionPhase getPhase() {
                return ActionPhase.FLAG;
            }

            @Override
            public void execute(ActionContext context) {
                executed.set(true);
            }
        };

        Rule firstRule = new Rule(
                new TrueCondition(),
                List.of(recordingAction),
                List.of()
        );

        Rule failingRule = new Rule(
                context -> {
                    throw new ConditionEvaluationException("failure");
                },
                List.of(),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(
                        ruleSet(root, firstRule, failingRule)
                ),
                registry
        );

        assertThrows(
                ConditionEvaluationException.class,
                () -> scanner.scan(file)
        );

        assertFalse(executed.get());

        assertEquals(
                Optional.of(FileState.ERROR),
                registry.getState(file)
        );
    }

    @Test
    void actionFailureSetsErrorAndPropagates() {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        Action failingAction = new Action() {
            @Override
            public ActionPhase getPhase() {
                return ActionPhase.FLAG;
            }

            @Override
            public void execute(ActionContext context)
                    throws ActionExecutionException {
                throw new ActionExecutionException("failure");
            }
        };

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(failingAction),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        assertThrows(
                ActionExecutionException.class,
                () -> scanner.scan(file)
        );

        assertEquals(
                Optional.of(FileState.ERROR),
                registry.getState(file)
        );
    }


    @Test
    void actionFailureAfterStateRemovalDoesNotRecreateActiveState() {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        Action removeState = new Action() {
            @Override
            public ActionPhase getPhase() {
                return ActionPhase.DELETE;
            }

            @Override
            public void execute(ActionContext context) {
                context.getStateRegistry().remove(
                        context.getFile().getPath()
                );
            }
        };

        Action failingLaterAction = new Action() {
            @Override
            public ActionPhase getPhase() {
                return ActionPhase.MIRROR;
            }

            @Override
            public void execute(ActionContext context)
                    throws ActionExecutionException {
                throw new ActionExecutionException("failure");
            }
        };

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(removeState, failingLaterAction),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        assertThrows(
                ActionExecutionException.class,
                () -> scanner.scan(file)
        );

        assertEquals(
                Optional.empty(),
                registry.getState(file)
        );
    }


    @Test
    void actionFailureAfterStateChangeDoesNotOverwriteStateWithError() {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data-state-preserve");
        Path file = root.resolve("file.txt");

        Action stateChangingFailure = new Action() {
            @Override
            public ActionPhase getPhase() {
                return ActionPhase.FLAG;
            }

            @Override
            public void execute(ActionContext context)
                    throws ActionExecutionException {
                context.getStateRegistry().setState(
                        context.getOriginalPath(),
                        FileState.FLAGGED
                );
                throw new ActionExecutionException("failure after state change");
            }
        };

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(stateChangingFailure),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        assertThrows(
                ActionExecutionException.class,
                () -> scanner.scan(file)
        );

        assertEquals(
                Optional.of(FileState.FLAGGED),
                registry.getState(file)
        );
    }

    @Test
    void scanReturnsCompletedSession() throws Exception {
        FileStateRegistry registry = new FileStateRegistry();
        Path root = tempDir.resolve("data");
        Path file = root.resolve("file.txt");

        Action action = new NoOpAction();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        FileScanner scanner = scanner(
                List.of(ruleSet(root, rule)),
                registry
        );

        ScanSession session = scanner.scan(file);

        assertEquals(
                file,
                session.getFile().getPath()
        );

        assertEquals(
                1,
                session.getScheduledActions().size()
        );
    }

    @Test
    void fileOutsideAllRuleSetsStillCountsAsScannedAndBecomesSafe()
            throws Exception {

        FileStateRegistry registry = new FileStateRegistry();

        RuleSet unrelated = ruleSet(
                tempDir.resolve("other"),
                new Rule(
                        new TrueCondition(),
                        List.of(new FlagAction()),
                        List.of()
                )
        );

        FileScanner scanner = scanner(
                List.of(unrelated),
                registry
        );

        Path file = tempDir.resolve("outside.txt");

        scanner.scan(file);

        assertEquals(
                Optional.of(FileState.SAFE),
                registry.getState(file)
        );
    }

    @Test
    void constructorRejectsNullCoordinator() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> new FileScanner(
                        null,
                        new ActionExecutor(registry),
                        registry
                )
        );
    }

    @Test
    void constructorRejectsNullActionExecutor() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> new FileScanner(
                        new ScanCoordinator(
                                new RuleSetIndex(List.of())
                        ),
                        null,
                        registry
                )
        );
    }

    @Test
    void constructorRejectsNullStateRegistry() {
        FileStateRegistry registry = new FileStateRegistry();

        assertThrows(
                NullPointerException.class,
                () -> new FileScanner(
                        new ScanCoordinator(
                                new RuleSetIndex(List.of())
                        ),
                        new ActionExecutor(registry),
                        null
                )
        );
    }

    @Test
    void scanRejectsNullPath() {
        FileStateRegistry registry = new FileStateRegistry();
        FileScanner scanner = scanner(List.of(), registry);

        assertThrows(
                NullPointerException.class,
                () -> scanner.scan(null)
        );
    }

    private FileScanner scanner(
            List<RuleSet> ruleSets,
            FileStateRegistry registry
    ) {
        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(ruleSets)
                );

        ActionExecutor executor =
                new ActionExecutor(registry);

        return new FileScanner(
                coordinator,
                executor,
                registry
        );
    }

    private RuleSet ruleSet(
            Path root,
            Rule... rules
    ) {
        RuleSet.Builder builder = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        );

        for (Rule rule : rules) {
            builder.rule(rule);
        }

        return builder.build();
    }
}
