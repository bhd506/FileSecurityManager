package com.haydeproductions.project.rule.condition.logic;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.FalseCondition;
import com.haydeproductions.project.rule.condition.TrueCondition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LogicalConditionTest {

    private final FileContext file = new FileContext(Path.of("file.txt"));

    @Test
    void andMatchesWhenEveryChildMatches() throws Exception {
        assertTrue(
                new AndCondition(
                        new TrueCondition(),
                        new TrueCondition()
                ).matches(file)
        );
    }

    @Test
    void andDoesNotMatchWhenAnyChildDoesNotMatch() throws Exception {
        assertFalse(
                new AndCondition(
                        new TrueCondition(),
                        new FalseCondition(),
                        new TrueCondition()
                ).matches(file)
        );
    }

    @Test
    void andShortCircuitsAfterFalse() throws Exception {
        AtomicBoolean reached = new AtomicBoolean(false);
        Condition later = context -> {
            reached.set(true);
            return true;
        };

        boolean result = new AndCondition(
                new FalseCondition(),
                later
        ).matches(file);

        assertFalse(result);
        assertFalse(reached.get());
    }

    @Test
    void orMatchesWhenAnyChildMatches() throws Exception {
        assertTrue(
                new OrCondition(
                        new FalseCondition(),
                        new TrueCondition(),
                        new FalseCondition()
                ).matches(file)
        );
    }

    @Test
    void orDoesNotMatchWhenEveryChildDoesNotMatch() throws Exception {
        assertFalse(
                new OrCondition(
                        new FalseCondition(),
                        new FalseCondition()
                ).matches(file)
        );
    }

    @Test
    void orShortCircuitsAfterTrue() throws Exception {
        AtomicBoolean reached = new AtomicBoolean(false);
        Condition later = context -> {
            reached.set(true);
            return false;
        };

        boolean result = new OrCondition(
                new TrueCondition(),
                later
        ).matches(file);

        assertTrue(result);
        assertFalse(reached.get());
    }

    @Test
    void notInvertsChild() throws Exception {
        assertFalse(
                new NotCondition(new TrueCondition()).matches(file)
        );

        assertTrue(
                new NotCondition(new FalseCondition()).matches(file)
        );
    }

    @Test
    void nestedConditionsComposeNormally() throws Exception {
        Condition condition = new AndCondition(
                new TrueCondition(),
                new OrCondition(
                        new FalseCondition(),
                        new NotCondition(new FalseCondition())
                )
        );

        assertTrue(condition.matches(file));
    }

    @Test
    void exceptionsPropagateThroughLogicalConditions() {
        Condition failing = context -> {
            throw new ConditionEvaluationException("failure");
        };

        assertThrows(
                ConditionEvaluationException.class,
                () -> new AndCondition(
                        new TrueCondition(),
                        failing
                ).matches(file)
        );

        assertThrows(
                ConditionEvaluationException.class,
                () -> new OrCondition(
                        new FalseCondition(),
                        failing
                ).matches(file)
        );

        assertThrows(
                ConditionEvaluationException.class,
                () -> new NotCondition(failing).matches(file)
        );
    }

    @Test
    void compositeConditionsDefensivelyCopyChildren() {
        List<Condition> source = new ArrayList<>();
        source.add(new TrueCondition());

        AndCondition condition = new AndCondition(source);
        source.add(new FalseCondition());

        assertEquals(1, condition.getConditions().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> condition.getConditions().add(new TrueCondition())
        );
    }

    @Test
    void andRejectsEmptyChildren() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AndCondition(List.of())
        );
    }

    @Test
    void orRejectsEmptyChildren() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrCondition(List.of())
        );
    }

    @Test
    void notRejectsNullChild() {
        assertThrows(
                NullPointerException.class,
                () -> new NotCondition(null)
        );
    }
}
