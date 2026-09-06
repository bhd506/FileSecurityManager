package com.haydeproductions.project.scan;

import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.action.ActionContext;
import com.haydeproductions.project.rule.action.ActionPhase;
import com.haydeproductions.project.rule.action.NoOpAction;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.TrueCondition;
import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScanCoordinatorTest {

    @TempDir
    Path tempDir;

    @Test
    void scanCreatesSessionForFile() throws Exception {
        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(List.of())
                );

        Path file = tempDir.resolve("file.txt");

        ScanSession session =
                coordinator.scan(file);

        assertNotNull(session);

        assertEquals(
                file,
                session.getFile().getPath()
        );
    }

    @Test
    void scanWithNoCandidateRuleSetsSchedulesNothing()
            throws Exception {

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(List.of())
                );

        ScanSession session =
                coordinator.scan(
                        tempDir.resolve("file.txt")
                );

        assertTrue(
                session.getScheduledActions().isEmpty()
        );
    }

    @Test
    void evaluatesCandidateRuleSet() throws Exception {
        Path root = tempDir.resolve("data");

        Action action = new NoOpAction();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                emptyOverrides()
        )
                .rule(rule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(ruleSet)
                        )
                );

        ScanSession session =
                coordinator.scan(
                        root.resolve("file.txt")
                );

        assertEquals(
                List.of(
                        new ScheduledAction(
                                rule,
                                action
                        )
                ),
                session.getScheduledActions()
        );
    }

    @Test
    void multipleRuleSetsUseSameScanSession()
            throws Exception {

        Path root = tempDir.resolve("data");
        Path nestedRoot = root.resolve("private");

        Action outerAction = new NoOpAction();
        Action innerAction = new NoOpAction();

        Rule outerRule = new Rule(
                new TrueCondition(),
                List.of(outerAction),
                List.of()
        );

        Rule innerRule = new Rule(
                new TrueCondition(),
                List.of(innerAction),
                List.of()
        );

        RuleSet outer = RuleSet.builder(
                root,
                emptyOverrides()
        )
                .rule(outerRule)
                .build();

        RuleSet inner = RuleSet.builder(
                nestedRoot,
                emptyOverrides()
        )
                .rule(innerRule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(
                                        outer,
                                        inner
                                )
                        )
                );

        ScanSession session =
                coordinator.scan(
                        nestedRoot.resolve("file.txt")
                );

        assertEquals(
                List.of(
                        new ScheduledAction(
                                outerRule,
                                outerAction
                        ),
                        new ScheduledAction(
                                innerRule,
                                innerAction
                        )
                ),
                session.getScheduledActions()
        );
    }

    @Test
    void preservesRuleSetConfigurationOrder()
            throws Exception {

        Path root = tempDir.resolve("data");
        Path nestedRoot = root.resolve("nested");

        Action firstAction = new NoOpAction();
        Action secondAction = new NoOpAction();

        Rule firstRule = new Rule(
                new TrueCondition(),
                List.of(firstAction),
                List.of()
        );

        Rule secondRule = new Rule(
                new TrueCondition(),
                List.of(secondAction),
                List.of()
        );

        RuleSet nested = RuleSet.builder(
                nestedRoot,
                emptyOverrides()
        )
                .rule(firstRule)
                .build();

        RuleSet parent = RuleSet.builder(
                root,
                emptyOverrides()
        )
                .rule(secondRule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(
                                        nested,
                                        parent
                                )
                        )
                );

        ScanSession session =
                coordinator.scan(
                        nestedRoot.resolve("file.txt")
                );

        assertEquals(
                List.of(
                        new ScheduledAction(
                                firstRule,
                                firstAction
                        ),
                        new ScheduledAction(
                                secondRule,
                                secondAction
                        )
                ),
                session.getScheduledActions()
        );
    }

    @Test
    void ruleSetRejectedByMaxDepthSchedulesNothing()
            throws Exception {

        Path root = tempDir.resolve("data");

        Action action = new NoOpAction();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                emptyOverrides()
        )
                .maxDepth(1)
                .rule(rule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(ruleSet)
                        )
                );

        ScanSession session =
                coordinator.scan(
                        root.resolve(
                                "one/two/file.txt"
                        )
                );

        assertTrue(
                session.getScheduledActions().isEmpty()
        );
    }

    @Test
    void ruleSetRejectedByOverrideSchedulesNothing()
            throws Exception {

        Path root = tempDir.resolve("data");
        Path overrideRoot = root.resolve("private");

        Action action = new NoOpAction();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        OverrideRegistry registry =
                new OverrideRegistry(
                        Set.of(overrideRoot)
                );

        RuleSet ruleSet = RuleSet.builder(
                root,
                registry
        )
                .rule(rule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(ruleSet)
                        )
                );

        ScanSession session =
                coordinator.scan(
                        overrideRoot.resolve("file.txt")
                );

        assertTrue(
                session.getScheduledActions().isEmpty()
        );
    }

    @Test
    void overrideBlocksParentButAllowsChildRuleSet()
            throws Exception {

        Path root = tempDir.resolve("data");
        Path overrideRoot = root.resolve("private");

        OverrideRegistry registry =
                new OverrideRegistry(
                        Set.of(overrideRoot)
                );

        Action parentAction = new NoOpAction();
        Action childAction = new NoOpAction();

        Rule parentRule = new Rule(
                new TrueCondition(),
                List.of(parentAction),
                List.of()
        );

        Rule childRule = new Rule(
                new TrueCondition(),
                List.of(childAction),
                List.of()
        );

        RuleSet parent = RuleSet.builder(
                root,
                registry
        )
                .rule(parentRule)
                .build();

        RuleSet child = RuleSet.builder(
                overrideRoot,
                registry
        )
                .rule(childRule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(
                                        parent,
                                        child
                                )
                        )
                );

        ScanSession session =
                coordinator.scan(
                        overrideRoot.resolve("file.txt")
                );

        assertEquals(
                List.of(
                        new ScheduledAction(
                                childRule,
                                childAction
                        )
                ),
                session.getScheduledActions()
        );
    }

    @Test
    void unrelatedRuleSetIsNeverEvaluated()
            throws Exception {

        Path relevantRoot =
                tempDir.resolve("relevant");

        Path unrelatedRoot =
                tempDir.resolve("unrelated");

        Action action = new NoOpAction();

        Rule relevantRule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        Rule unrelatedRule = new Rule(
                file -> {
                    throw new ConditionEvaluationException(
                            "Unrelated rule must not run"
                    );
                },
                List.of(),
                List.of()
        );

        RuleSet relevant = RuleSet.builder(
                relevantRoot,
                emptyOverrides()
        )
                .rule(relevantRule)
                .build();

        RuleSet unrelated = RuleSet.builder(
                unrelatedRoot,
                emptyOverrides()
        )
                .rule(unrelatedRule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(
                                        relevant,
                                        unrelated
                                )
                        )
                );

        assertDoesNotThrow(
                () -> coordinator.scan(
                        relevantRoot.resolve("file.txt")
                )
        );
    }

    @Test
    void conditionEvaluationFailurePropagates() {
        Path root = tempDir.resolve("data");

        Rule failingRule = new Rule(
                file -> {
                    throw new ConditionEvaluationException(
                            "failure"
                    );
                },
                List.of(new NoOpAction()),
                List.of(new NoOpAction())
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                emptyOverrides()
        )
                .rule(failingRule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(ruleSet)
                        )
                );

        assertThrows(
                ConditionEvaluationException.class,
                () -> coordinator.scan(
                        root.resolve("file.txt")
                )
        );
    }

    @Test
    void actionsRemainScheduledAndAreNotExecutedDuringScan()
            throws Exception {

        Path root = tempDir.resolve("data");

        RecordingAction action =
                new RecordingAction();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                emptyOverrides()
        )
                .rule(rule)
                .build();

        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(
                                List.of(ruleSet)
                        )
                );

        ScanSession session =
                coordinator.scan(
                        root.resolve("file.txt")
                );

        assertFalse(action.wasExecuted());

        assertEquals(
                1,
                session.getScheduledActions().size()
        );
    }

    @Test
    void constructorRejectsNullRuleSetIndex() {
        assertThrows(
                NullPointerException.class,
                () -> new ScanCoordinator(null)
        );
    }

    @Test
    void scanRejectsNullPath() {
        ScanCoordinator coordinator =
                new ScanCoordinator(
                        new RuleSetIndex(List.of())
                );

        assertThrows(
                NullPointerException.class,
                () -> coordinator.scan(null)
        );
    }

    private OverrideRegistry emptyOverrides() {
        return new OverrideRegistry(Set.of());
    }

    private static final class RecordingAction
            implements Action {

        private boolean executed;

        @Override
        public ActionPhase getPhase() {
            return ActionPhase.FLAG;
        }

        @Override
        public void execute(ActionContext context) {
            executed = true;
        }

        boolean wasExecuted() {
            return executed;
        }
    }
}
