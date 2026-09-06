package com.haydeproductions.project.rule.condition;

import com.haydeproductions.project.rule.FileContext;

public interface Condition {

    boolean matches(FileContext file)
            throws ConditionEvaluationException;
}