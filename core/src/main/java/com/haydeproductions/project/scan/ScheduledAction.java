package com.haydeproductions.project.scan;

import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.Action;

import java.util.Objects;

public record ScheduledAction(
        Rule rule,
        Action action
) {
    public ScheduledAction {
        Objects.requireNonNull(rule);
        Objects.requireNonNull(action);
    }
}