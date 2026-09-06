package com.haydeproductions.project.rule.action;

public final class NoOpAction implements Action {

    @Override
    public void execute(ActionContext context) {
        // Intentionally does nothing.
    }
}