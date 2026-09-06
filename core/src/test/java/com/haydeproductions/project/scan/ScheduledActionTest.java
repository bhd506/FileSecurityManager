package com.haydeproductions.project.scan;

import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.action.NoOpAction;
import com.haydeproductions.project.rule.condition.TrueCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduledActionTest {

    @Test
    void storesRuleAndAction() {
        Rule rule = new Rule(
                new TrueCondition(),
                List.of(),
                List.of()
        );

        Action action = new NoOpAction();

        ScheduledAction scheduled =
                new ScheduledAction(rule, action);

        assertSame(rule, scheduled.rule());
        assertSame(action, scheduled.action());
    }

    @Test
    void rejectsNullRule() {
        assertThrows(
                NullPointerException.class,
                () -> new ScheduledAction(
                        null,
                        new NoOpAction()
                )
        );
    }

    @Test
    void rejectsNullAction() {
        Rule rule = new Rule(
                new TrueCondition(),
                List.of(),
                List.of()
        );

        assertThrows(
                NullPointerException.class,
                () -> new ScheduledAction(
                        rule,
                        null
                )
        );
    }
}
