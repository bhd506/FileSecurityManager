package com.haydeproductions.project.rule.condition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionEvaluationExceptionTest {

    @Test
    void storesMessage() {
        ConditionEvaluationException exception =
                new ConditionEvaluationException("failure");

        assertEquals(
                "failure",
                exception.getMessage()
        );
    }

    @Test
    void storesMessageAndCause() {
        RuntimeException cause =
                new RuntimeException("cause");

        ConditionEvaluationException exception =
                new ConditionEvaluationException(
                        "failure",
                        cause
                );

        assertEquals(
                "failure",
                exception.getMessage()
        );

        assertSame(
                cause,
                exception.getCause()
        );
    }
}
