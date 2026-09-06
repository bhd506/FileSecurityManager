package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.condition.TrueCondition;
import com.haydeproductions.project.scan.ScanSession;
import com.haydeproductions.project.state.FileStateRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActionExecutorTest {

    @Test
    void executesScheduledAction() throws Exception {
        List<String> executions = new ArrayList<>();

        Action action = new RecordingAction(
                ActionPhase.FLAG,
                "flag",
                executions
        );

        Rule rule = ruleWithActions(action);
        ScanSession session = session();
        session.evaluate(rule);

        new ActionExecutor(new FileStateRegistry())
                .execute(session);

        assertEquals(
                List.of("flag"),
                executions
        );
    }

    @Test
    void executesActionsInPhaseOrder() throws Exception {
        List<String> executions = new ArrayList<>();

        Action delete = new RecordingAction(
                ActionPhase.DELETE,
                "delete",
                executions
        );

        Action mirror = new RecordingAction(
                ActionPhase.MIRROR,
                "mirror",
                executions
        );

        Action flag = new RecordingAction(
                ActionPhase.FLAG,
                "flag",
                executions
        );

        Action quarantine = new RecordingAction(
                ActionPhase.QUARANTINE,
                "quarantine",
                executions
        );

        Rule rule = ruleWithActions(
                delete,
                mirror,
                flag,
                quarantine
        );

        ScanSession session = session();
        session.evaluate(rule);

        new ActionExecutor(new FileStateRegistry())
                .execute(session);

        assertEquals(
                List.of(
                        "flag",
                        "quarantine",
                        "delete",
                        "mirror"
                ),
                executions
        );
    }

    @Test
    void preservesSchedulingOrderWithinSamePhase()
            throws Exception {

        List<String> executions = new ArrayList<>();

        Action first = new RecordingAction(
                ActionPhase.FLAG,
                "first",
                executions
        );

        Action second = new RecordingAction(
                ActionPhase.FLAG,
                "second",
                executions
        );

        Action third = new RecordingAction(
                ActionPhase.FLAG,
                "third",
                executions
        );

        Rule rule = ruleWithActions(
                first,
                second,
                third
        );

        ScanSession session = session();
        session.evaluate(rule);

        new ActionExecutor(new FileStateRegistry())
                .execute(session);

        assertEquals(
                List.of(
                        "first",
                        "second",
                        "third"
                ),
                executions
        );
    }

    @Test
    void preservesOrderAcrossRulesWithinSamePhase()
            throws Exception {

        List<String> executions = new ArrayList<>();

        Action first = new RecordingAction(
                ActionPhase.FLAG,
                "first",
                executions
        );

        Action second = new RecordingAction(
                ActionPhase.FLAG,
                "second",
                executions
        );

        Rule firstRule = ruleWithActions(first);
        Rule secondRule = ruleWithActions(second);

        ScanSession session = session();

        session.evaluate(firstRule);
        session.evaluate(secondRule);

        new ActionExecutor(new FileStateRegistry())
                .execute(session);

        assertEquals(
                List.of(
                        "first",
                        "second"
                ),
                executions
        );
    }

    @Test
    void duplicateActionsAreNotDeduplicated()
            throws Exception {

        List<String> executions = new ArrayList<>();

        Action first = new RecordingAction(
                ActionPhase.FLAG,
                "flag",
                executions
        );

        Action second = new RecordingAction(
                ActionPhase.FLAG,
                "flag",
                executions
        );

        Rule rule = ruleWithActions(first, second);

        ScanSession session = session();
        session.evaluate(rule);

        new ActionExecutor(new FileStateRegistry())
                .execute(session);

        assertEquals(
                List.of(
                        "flag",
                        "flag"
                ),
                executions
        );
    }

    @Test
    void actionsFromMultipleRulesAreGloballyPhaseOrdered()
            throws Exception {

        List<String> executions = new ArrayList<>();

        Rule firstRule = ruleWithActions(
                new RecordingAction(
                        ActionPhase.DELETE,
                        "delete-1",
                        executions
                ),
                new RecordingAction(
                        ActionPhase.FLAG,
                        "flag-1",
                        executions
                )
        );

        Rule secondRule = ruleWithActions(
                new RecordingAction(
                        ActionPhase.QUARANTINE,
                        "quarantine-1",
                        executions
                ),
                new RecordingAction(
                        ActionPhase.FLAG,
                        "flag-2",
                        executions
                ),
                new RecordingAction(
                        ActionPhase.DELETE,
                        "delete-2",
                        executions
                )
        );

        ScanSession session = session();

        session.evaluate(firstRule);
        session.evaluate(secondRule);

        new ActionExecutor(new FileStateRegistry())
                .execute(session);

        assertEquals(
                List.of(
                        "flag-1",
                        "flag-2",
                        "quarantine-1",
                        "delete-1",
                        "delete-2"
                ),
                executions
        );
    }

    @Test
    void emptySessionExecutesNothing()
            throws Exception {

        ScanSession session = session();

        assertDoesNotThrow(
                () -> new ActionExecutor(
                        new FileStateRegistry()
                ).execute(session)
        );
    }

    @Test
    void executorUsesSessionsFileContext()
            throws Exception {

        Path path = Path.of("specific-file.txt");

        RecordingContextAction action =
                new RecordingContextAction();

        Rule rule = ruleWithActions(action);

        ScanSession session =
                new ScanSession(
                        new FileContext(path)
                );

        session.evaluate(rule);

        new ActionExecutor(new FileStateRegistry())
                .execute(session);

        assertSame(
                session.getFile(),
                action.receivedFile
        );
    }

    @Test
    void executorPassesConfiguredStateRegistryToActions()
            throws Exception {

        FileStateRegistry registry =
                new FileStateRegistry();

        RecordingContextAction action =
                new RecordingContextAction();

        Rule rule = ruleWithActions(action);

        ScanSession session = session();
        session.evaluate(rule);

        new ActionExecutor(registry)
                .execute(session);

        assertSame(
                registry,
                action.receivedRegistry
        );
    }

    @Test
    void executionFailurePropagates() throws Exception {
        Action failing = new Action() {

            @Override
            public ActionPhase getPhase() {
                return ActionPhase.FLAG;
            }

            @Override
            public void execute(ActionContext context)
                    throws ActionExecutionException {

                throw new ActionExecutionException(
                        "failure"
                );
            }
        };

        Rule rule = ruleWithActions(failing);

        ScanSession session = session();
        session.evaluate(rule);

        assertThrows(
                ActionExecutionException.class,
                () -> new ActionExecutor(
                        new FileStateRegistry()
                ).execute(session)
        );
    }

    @Test
    void constructorRejectsNullStateRegistry() {
        assertThrows(
                NullPointerException.class,
                () -> new ActionExecutor(null)
        );
    }

    @Test
    void executorRejectsNullSession() {
        ActionExecutor executor =
                new ActionExecutor(
                        new FileStateRegistry()
                );

        assertThrows(
                NullPointerException.class,
                () -> executor.execute(null)
        );
    }

    private ScanSession session() {
        return new ScanSession(
                new FileContext(
                        Path.of("file.txt")
                )
        );
    }

    private Rule ruleWithActions(Action... actions) {
        return new Rule(
                new TrueCondition(),
                List.of(actions),
                List.of()
        );
    }

    private static final class RecordingAction
            implements Action {

        private final ActionPhase phase;
        private final String value;
        private final List<String> executions;

        private RecordingAction(
                ActionPhase phase,
                String value,
                List<String> executions
        ) {
            this.phase = phase;
            this.value = value;
            this.executions = executions;
        }

        @Override
        public ActionPhase getPhase() {
            return phase;
        }

        @Override
        public void execute(ActionContext context) {
            executions.add(value);
        }
    }

    private static final class RecordingContextAction
            implements Action {

        private FileContext receivedFile;
        private FileStateRegistry receivedRegistry;

        @Override
        public ActionPhase getPhase() {
            return ActionPhase.FLAG;
        }

        @Override
        public void execute(ActionContext context) {
            receivedFile = context.getFile();
            receivedRegistry = context.getStateRegistry();
        }
    }
}
