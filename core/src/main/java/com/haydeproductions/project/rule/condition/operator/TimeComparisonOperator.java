package com.haydeproductions.project.rule.condition.operator;

import java.time.Instant;

public enum TimeComparisonOperator {
    BEFORE,
    BEFORE_OR_EQUAL,
    EQUAL,
    AFTER_OR_EQUAL,
    AFTER;

    public boolean test(Instant actual, Instant expected) {
        int comparison = actual.compareTo(expected);

        return switch (this) {
            case BEFORE -> comparison < 0;
            case BEFORE_OR_EQUAL -> comparison <= 0;
            case EQUAL -> comparison == 0;
            case AFTER_OR_EQUAL -> comparison >= 0;
            case AFTER -> comparison > 0;
        };
    }
}
