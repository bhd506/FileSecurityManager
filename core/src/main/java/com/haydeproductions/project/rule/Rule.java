package com.haydeproductions.project.rule;

import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;

import java.util.List;
import java.util.Objects;

public final class Rule {

    private final Condition condition;
    private final List<Action> onMatch;
    private final List<Action> onNoMatch;

    public Rule(
            Condition condition,
            List<Action> onMatch,
            List<Action> onNoMatch
    ) {
        this.condition = Objects.requireNonNull(condition);
        this.onMatch = List.copyOf(onMatch);
        this.onNoMatch = List.copyOf(onNoMatch);
    }

    public RuleResult evaluate(FileContext file)
            throws ConditionEvaluationException {

        Objects.requireNonNull(file);

        return condition.matches(file)
                ? RuleResult.MATCH
                : RuleResult.NO_MATCH;
    }

    public List<Action> getActions(RuleResult result) {
        Objects.requireNonNull(result);

        return switch (result) {
            case MATCH -> onMatch;
            case NO_MATCH -> onNoMatch;
        };
    }

    public Condition getCondition() {
        return condition;
    }

    public List<Action> getOnMatch() {
        return onMatch;
    }

    public List<Action> getOnNoMatch() {
        return onNoMatch;
    }
}