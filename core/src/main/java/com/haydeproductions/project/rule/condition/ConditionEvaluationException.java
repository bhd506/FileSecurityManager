package com.haydeproductions.project.rule.condition;

public class ConditionEvaluationException extends Exception {

    public ConditionEvaluationException(String message) {
        super(message);
    }

    public ConditionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}