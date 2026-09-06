package com.haydeproductions.project.rule.action;

public interface Action {

    ActionPhase getPhase();

    void execute(ActionContext context)
            throws ActionExecutionException;
}