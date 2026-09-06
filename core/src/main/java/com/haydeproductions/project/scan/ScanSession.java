package com.haydeproductions.project.scan;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.RuleResult;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ScanSession {

    private final FileContext file;
    private final List<ScheduledAction> scheduledActions = new ArrayList<>();

    public ScanSession(FileContext file) {
        this.file = Objects.requireNonNull(file);
    }

    public FileContext getFile() {
        return file;
    }

    public RuleResult evaluate(Rule rule)
            throws ConditionEvaluationException {

        Objects.requireNonNull(rule);

        RuleResult result = rule.evaluate(file);

        for (Action action : rule.getActions(result)) {
            scheduledActions.add(
                    new ScheduledAction(rule, action)
            );
        }

        return result;
    }

    public List<ScheduledAction> getScheduledActions() {
        return List.copyOf(scheduledActions);
    }
}