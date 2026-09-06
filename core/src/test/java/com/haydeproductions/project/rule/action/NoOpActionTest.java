package com.haydeproductions.project.rule.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoOpActionTest {

    @Test
    void defaultPhaseIsFlag() {
        NoOpAction action =
                new NoOpAction();

        assertEquals(
                ActionPhase.FLAG,
                action.getPhase()
        );
    }

    @Test
    void acceptsExplicitPhase() {
        NoOpAction action =
                new NoOpAction(
                        ActionPhase.DELETE
                );

        assertEquals(
                ActionPhase.DELETE,
                action.getPhase()
        );
    }

    @Test
    void rejectsNullPhase() {
        assertThrows(
                NullPointerException.class,
                () -> new NoOpAction(null)
        );
    }
}
