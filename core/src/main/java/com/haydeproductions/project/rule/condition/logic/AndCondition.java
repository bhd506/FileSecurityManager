package com.haydeproductions.project.rule.condition.logic;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class AndCondition implements Condition {

    private final List<Condition> conditions;

    public AndCondition(Collection<? extends Condition> conditions) {
        Objects.requireNonNull(conditions);
        this.conditions = List.copyOf(conditions);

        if (this.conditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "AndCondition requires at least one child condition"
            );
        }
    }

    public AndCondition(Condition... conditions) {
        this(Arrays.asList(Objects.requireNonNull(conditions)));
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    @Override
    public boolean matches(FileContext file)
            throws ConditionEvaluationException {

        Objects.requireNonNull(file);

        for (Condition condition : conditions) {
            if (!condition.matches(file)) {
                return false;
            }
        }

        return true;
    }
}
