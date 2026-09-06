package com.haydeproductions.project.rule.action;

import java.util.Objects;

public final class NoOpAction implements Action {

    private final ActionPhase phase;

    public NoOpAction() {
        this(ActionPhase.FLAG);
    }

    public NoOpAction(ActionPhase phase) {
        this.phase = Objects.requireNonNull(phase);
    }

    @Override
    public ActionPhase getPhase() {
        return phase;
    }

    @Override
    public void execute(ActionContext context) {
        // Intentionally does nothing.
    }
}