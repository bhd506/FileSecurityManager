package com.haydeproductions.project.rule.action;

public interface Action {

    void execute(ActionContext context)
            throws ActionExecutionException;
}