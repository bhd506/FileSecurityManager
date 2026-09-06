package com.haydeproductions.project.scan;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.RuleResult;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.action.NoOpAction;
import com.haydeproductions.project.rule.condition.FalseCondition;
import com.haydeproductions.project.rule.condition.TrueCondition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScanSessionTest {

    @Test
    void schedulesMatchActions() throws Exception {
        Action action = new NoOpAction();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        ScanSession session = new ScanSession(
                new FileContext(Path.of("file.txt"))
        );

        assertEquals(
                RuleResult.MATCH,
                session.evaluate(rule)
        );

        assertEquals(
                List.of(new ScheduledAction(rule, action)),
                session.getScheduledActions()
        );
    }

    @Test
    void schedulesNoMatchActions() throws Exception {
        Action action = new NoOpAction();

        Rule rule = new Rule(
                new FalseCondition(),
                List.of(),
                List.of(action)
        );

        ScanSession session = new ScanSession(
                new FileContext(Path.of("file.txt"))
        );

        assertEquals(
                RuleResult.NO_MATCH,
                session.evaluate(rule)
        );

        assertEquals(
                List.of(new ScheduledAction(rule, action)),
                session.getScheduledActions()
        );
    }

    @Test
    void accumulatesActionsAcrossRulesInEvaluationOrder()
            throws Exception {

        Action first = new NoOpAction();
        Action second = new NoOpAction();
        Action third = new NoOpAction();

        Rule rule1 = new Rule(
                new TrueCondition(),
                List.of(first, second),
                List.of()
        );

        Rule rule2 = new Rule(
                new FalseCondition(),
                List.of(),
                List.of(third)
        );

        ScanSession session = new ScanSession(
                new FileContext(Path.of("file.txt"))
        );

        session.evaluate(rule1);
        session.evaluate(rule2);

        assertEquals(
                List.of(
                        new ScheduledAction(rule1, first),
                        new ScheduledAction(rule1, second),
                        new ScheduledAction(rule2, third)
                ),
                session.getScheduledActions()
        );
    }

    @Test
    void scheduledActionListIsImmutable()
            throws Exception {

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(new NoOpAction()),
                List.of()
        );

        ScanSession session = new ScanSession(
                new FileContext(Path.of("file.txt"))
        );

        session.evaluate(rule);

        assertThrows(
                UnsupportedOperationException.class,
                () -> session
                        .getScheduledActions()
                        .add(new ScheduledAction(
                                rule,
                                new NoOpAction()
                        ))
        );
    }

    @Test
    void failedConditionDoesNotScheduleActions() {
        Rule rule = new Rule(
                file -> {
                    throw new com.haydeproductions.project.rule.condition.ConditionEvaluationException(
                            "failure"
                    );
                },
                List.of(new NoOpAction()),
                List.of(new NoOpAction())
        );

        ScanSession session = new ScanSession(
                new FileContext(Path.of("file.txt"))
        );

        assertThrows(
                com.haydeproductions.project.rule.condition.ConditionEvaluationException.class,
                () -> session.evaluate(rule)
        );

        assertTrue(
                session.getScheduledActions().isEmpty()
        );
    }

    @Test
    void returnsSuppliedFileContext() {
        FileContext file =
                new FileContext(Path.of("file.txt"));

        ScanSession session =
                new ScanSession(file);

        assertSame(
                file,
                session.getFile()
        );
    }

    @Test
    void rejectsNullFileContext() {
        assertThrows(
                NullPointerException.class,
                () -> new ScanSession(null)
        );
    }

    @Test
    void rejectsNullRule() {
        ScanSession session = new ScanSession(
                new FileContext(Path.of("file.txt"))
        );

        assertThrows(
                NullPointerException.class,
                () -> session.evaluate(null)
        );
    }

    @Test
    void duplicateScheduledActionsArePreserved() throws Exception {
        Action action = new NoOpAction();

        Rule firstRule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        Rule secondRule = new Rule(
                new TrueCondition(),
                List.of(action),
                List.of()
        );

        ScanSession session = new ScanSession(
                new FileContext(Path.of("file.txt"))
        );

        session.evaluate(firstRule);
        session.evaluate(secondRule);

        assertEquals(
                List.of(
                        new ScheduledAction(firstRule, action),
                        new ScheduledAction(secondRule, action)
                ),
                session.getScheduledActions()
        );
    }

}