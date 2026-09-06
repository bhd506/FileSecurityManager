package com.haydeproductions.project.rule.condition.logic;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;

import java.util.Objects;

public final class NotCondition implements Condition {

    private final Condition condition;

    public NotCondition(Condition condition) {
        this.condition = Objects.requireNonNull(condition);
    }

    public Condition getCondition() {
        return condition;
    }

    @Override
    public boolean matches(FileContext file)
            throws ConditionEvaluationException {
        return !condition.matches(Objects.requireNonNull(file));
    }
}
