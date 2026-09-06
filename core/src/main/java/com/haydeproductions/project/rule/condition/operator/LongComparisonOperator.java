package com.haydeproductions.project.rule.condition.operator;

public enum LongComparisonOperator {
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL;

    public boolean test(long actual, long expected) {
        return switch (this) {
            case EQUAL -> actual == expected;
            case NOT_EQUAL -> actual != expected;
            case LESS_THAN -> actual < expected;
            case LESS_THAN_OR_EQUAL -> actual <= expected;
            case GREATER_THAN -> actual > expected;
            case GREATER_THAN_OR_EQUAL -> actual >= expected;
        };
    }
}
