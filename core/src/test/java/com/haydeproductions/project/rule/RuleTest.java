package com.haydeproductions.project.rule;

import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.action.NoOpAction;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.FalseCondition;
import com.haydeproductions.project.rule.condition.TrueCondition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleTest {

    @Test
    void returnsMatchWhenConditionMatches() throws Exception {
        Rule rule = new Rule(
                new TrueCondition(),
                List.of(),
                List.of()
        );

        FileContext file =
                new FileContext(Path.of("file.txt"));

        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(file)
        );
    }

    @Test
    void returnsNoMatchWhenConditionDoesNotMatch() throws Exception {
        Rule rule = new Rule(
                new FalseCondition(),
                List.of(),
                List.of()
        );

        FileContext file =
                new FileContext(Path.of("file.txt"));

        assertEquals(
                RuleResult.NO_MATCH,
                rule.evaluate(file)
        );
    }

    @Test
    void propagatesConditionEvaluationException() {
        Condition condition = file -> {
            throw new ConditionEvaluationException("test failure");
        };

        Rule rule = new Rule(
                condition,
                List.of(),
                List.of()
        );

        FileContext file =
                new FileContext(Path.of("file.txt"));

        assertThrows(
                ConditionEvaluationException.class,
                () -> rule.evaluate(file)
        );
    }

    @Test
    void returnsMatchActionsForMatchResult() {
        Action matchAction = new NoOpAction();
        Action noMatchAction = new NoOpAction();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(matchAction),
                List.of(noMatchAction)
        );

        assertEquals(
                List.of(matchAction),
                rule.getActions(RuleResult.MATCH)
        );
    }

    @Test
    void returnsNoMatchActionsForNoMatchResult() {
        Action matchAction = new NoOpAction();
        Action noMatchAction = new NoOpAction();

        Rule rule = new Rule(
                new FalseCondition(),
                List.of(matchAction),
                List.of(noMatchAction)
        );

        assertEquals(
                List.of(noMatchAction),
                rule.getActions(RuleResult.NO_MATCH)
        );
    }

    @Test
    void preservesActionOrder() {
        Action first = new NoOpAction();
        Action second = new NoOpAction();
        Action third = new NoOpAction();

        Rule rule = new Rule(
                new TrueCondition(),
                List.of(first, second, third),
                List.of()
        );

        assertEquals(
                List.of(first, second, third),
                rule.getActions(RuleResult.MATCH)
        );
    }

    @Test
    void actionListsAreImmutable() {
        Rule rule = new Rule(
                new TrueCondition(),
                List.of(new NoOpAction()),
                List.of()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> rule.getOnMatch().add(new NoOpAction())
        );
    }

    @Test
    void defensivelyCopiesMatchActionList() {
        List<Action> source = new ArrayList<>();
        Action first = new NoOpAction();

        source.add(first);

        Rule rule = new Rule(
                new TrueCondition(),
                source,
                List.of()
        );

        source.clear();
        source.add(new NoOpAction());

        assertEquals(
                List.of(first),
                rule.getOnMatch()
        );
    }

    @Test
    void defensivelyCopiesNoMatchActionList() {
        List<Action> source = new ArrayList<>();
        Action first = new NoOpAction();

        source.add(first);

        Rule rule = new Rule(
                new FalseCondition(),
                List.of(),
                source
        );

        source.clear();
        source.add(new NoOpAction());

        assertEquals(
                List.of(first),
                rule.getOnNoMatch()
        );
    }

    @Test
    void rejectsNullCondition() {
        assertThrows(
                NullPointerException.class,
                () -> new Rule(
                        null,
                        List.of(),
                        List.of()
                )
        );
    }

    @Test
    void rejectsNullMatchActionList() {
        assertThrows(
                NullPointerException.class,
                () -> new Rule(
                        new TrueCondition(),
                        null,
                        List.of()
                )
        );
    }

    @Test
    void rejectsNullNoMatchActionList() {
        assertThrows(
                NullPointerException.class,
                () -> new Rule(
                        new TrueCondition(),
                        List.of(),
                        null
                )
        );
    }

    @Test
    void rejectsNullActionInsideMatchList() {
        List<Action> actions = new ArrayList<>();
        actions.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new Rule(
                        new TrueCondition(),
                        actions,
                        List.of()
                )
        );
    }

    @Test
    void rejectsNullFileDuringEvaluation() {
        Rule rule = new Rule(
                new TrueCondition(),
                List.of(),
                List.of()
        );

        assertThrows(
                NullPointerException.class,
                () -> rule.evaluate(null)
        );
    }

    @Test
    void rejectsNullResultWhenGettingActions() {
        Rule rule = new Rule(
                new TrueCondition(),
                List.of(),
                List.of()
        );

        assertThrows(
                NullPointerException.class,
                () -> rule.getActions(null)
        );
    }

}