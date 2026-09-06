package com.haydeproductions.project.rule.condition.metadata;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.operator.LongComparisonOperator;

import java.io.IOException;
import java.util.Objects;

public final class SizeCondition implements Condition {

    private final LongComparisonOperator operator;
    private final long value;
    private final Long upperInclusive;

    public SizeCondition(
            LongComparisonOperator operator,
            long value
    ) {
        if (value < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }

        this.operator = Objects.requireNonNull(operator);
        this.value = value;
        this.upperInclusive = null;
    }

    private SizeCondition(long lowerInclusive, long upperInclusive) {
        if (lowerInclusive < 0 || upperInclusive < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }

        if (lowerInclusive > upperInclusive) {
            throw new IllegalArgumentException(
                    "Lower size bound cannot exceed upper size bound"
            );
        }

        this.operator = null;
        this.value = lowerInclusive;
        this.upperInclusive = upperInclusive;
    }

    public static SizeCondition betweenInclusive(
            long lowerInclusive,
            long upperInclusive
    ) {
        return new SizeCondition(lowerInclusive, upperInclusive);
    }

    @Override
    public boolean matches(FileContext file)
            throws ConditionEvaluationException {
        Objects.requireNonNull(file);

        try {
            long actual = file.getSize();

            if (upperInclusive != null) {
                return actual >= value && actual <= upperInclusive;
            }

            return operator.test(actual, value);
        } catch (IOException exception) {
            throw new ConditionEvaluationException(
                    "Failed to read file size: " + file.getNormalizedPath(),
                    exception
            );
        }
    }
}
