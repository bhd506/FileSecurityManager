package com.haydeproductions.project.rule.condition.metadata;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.operator.TimeComparisonOperator;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class ModifiedTimeCondition implements Condition {

    private enum Mode {
        ABSOLUTE,
        OLDER_THAN,
        NEWER_THAN
    }

    private final Mode mode;
    private final TimeComparisonOperator operator;
    private final Instant reference;
    private final Duration age;
    private final Clock clock;

    public ModifiedTimeCondition(
            TimeComparisonOperator operator,
            Instant reference
    ) {
        this.mode = Mode.ABSOLUTE;
        this.operator = Objects.requireNonNull(operator);
        this.reference = Objects.requireNonNull(reference);
        this.age = null;
        this.clock = null;
    }

    private ModifiedTimeCondition(
            Mode mode,
            Duration age,
            Clock clock
    ) {
        if (age.isNegative()) {
            throw new IllegalArgumentException("Age cannot be negative");
        }

        this.mode = mode;
        this.operator = null;
        this.reference = null;
        this.age = age;
        this.clock = clock;
    }

    public static ModifiedTimeCondition olderThan(Duration age) {
        return olderThan(age, Clock.systemUTC());
    }

    public static ModifiedTimeCondition olderThan(
            Duration age,
            Clock clock
    ) {
        return new ModifiedTimeCondition(
                Mode.OLDER_THAN,
                Objects.requireNonNull(age),
                Objects.requireNonNull(clock)
        );
    }

    public static ModifiedTimeCondition newerThan(Duration age) {
        return newerThan(age, Clock.systemUTC());
    }

    public static ModifiedTimeCondition newerThan(
            Duration age,
            Clock clock
    ) {
        return new ModifiedTimeCondition(
                Mode.NEWER_THAN,
                Objects.requireNonNull(age),
                Objects.requireNonNull(clock)
        );
    }

    @Override
    public boolean matches(FileContext file)
            throws ConditionEvaluationException {
        Objects.requireNonNull(file);

        try {
            Instant actual = file.getLastModifiedTime().toInstant();

            return switch (mode) {
                case ABSOLUTE -> operator.test(actual, reference);
                case OLDER_THAN -> actual.isBefore(
                        clock.instant().minus(age)
                );
                case NEWER_THAN -> actual.isAfter(
                        clock.instant().minus(age)
                );
            };
        } catch (IOException exception) {
            throw new ConditionEvaluationException(
                    "Failed to read last-modified time: "
                            + file.getNormalizedPath(),
                    exception
            );
        }
    }
}
