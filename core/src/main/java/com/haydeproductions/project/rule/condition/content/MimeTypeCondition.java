package com.haydeproductions.project.rule.condition.content;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class MimeTypeCondition implements Condition {

    private final Set<String> patterns;
    private final MembershipOperator operator;

    public MimeTypeCondition(String pattern) {
        this(Set.of(pattern), MembershipOperator.IN);
    }

    public MimeTypeCondition(Collection<String> patterns) {
        this(patterns, MembershipOperator.IN);
    }

    public MimeTypeCondition(
            Collection<String> patterns,
            MembershipOperator operator
    ) {
        Objects.requireNonNull(patterns);
        this.operator = Objects.requireNonNull(operator);

        if (patterns.isEmpty()) {
            throw new IllegalArgumentException(
                    "MimeTypeCondition requires at least one MIME pattern"
            );
        }

        this.patterns = patterns.stream()
                .map(Objects::requireNonNull)
                .map(MimeTypeCondition::normalizePattern)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean matches(FileContext file)
            throws ConditionEvaluationException {
        Objects.requireNonNull(file);

        try {
            String actual = file.getMimeType()
                    .toLowerCase(Locale.ROOT);

            boolean contained = patterns.stream()
                    .anyMatch(pattern -> matchesPattern(actual, pattern));

            return operator.apply(contained);
        } catch (IOException exception) {
            throw new ConditionEvaluationException(
                    "Failed to detect MIME type: "
                            + file.getNormalizedPath(),
                    exception
            );
        }
    }

    private static String normalizePattern(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);

        if (!normalized.matches(
                "(?:\\*/\\*|[a-z0-9!#$&^_.+\\-]+/(?:\\*|[a-z0-9!#$&^_.+\\-]+))"
        )) {
            throw new IllegalArgumentException(
                    "Invalid MIME type pattern: " + value
            );
        }

        return normalized;
    }

    private static boolean matchesPattern(
            String actual,
            String pattern
    ) {
        if (pattern.equals("*/*")) {
            return true;
        }

        if (pattern.endsWith("/*")) {
            String type = pattern.substring(0, pattern.length() - 1);
            return actual.startsWith(type);
        }

        return actual.equals(pattern);
    }
}
