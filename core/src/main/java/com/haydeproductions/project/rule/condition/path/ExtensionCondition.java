package com.haydeproductions.project.rule.condition.path;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ExtensionCondition implements Condition {

    private final Set<String> extensions;
    private final MembershipOperator operator;
    private final boolean caseSensitive;

    public ExtensionCondition(String extension) {
        this(Set.of(extension), MembershipOperator.IN, false);
    }

    public ExtensionCondition(Collection<String> extensions) {
        this(extensions, MembershipOperator.IN, false);
    }

    public ExtensionCondition(
            Collection<String> extensions,
            MembershipOperator operator,
            boolean caseSensitive
    ) {
        Objects.requireNonNull(extensions);
        this.operator = Objects.requireNonNull(operator);
        this.caseSensitive = caseSensitive;

        if (extensions.isEmpty()) {
            throw new IllegalArgumentException(
                    "ExtensionCondition requires at least one extension"
            );
        }

        this.extensions = extensions.stream()
                .map(Objects::requireNonNull)
                .map(ExtensionCondition::stripLeadingDot)
                .peek(value -> {
                    if (value.isBlank()) {
                        throw new IllegalArgumentException(
                                "Extension cannot be blank"
                        );
                    }
                })
                .map(value -> caseSensitive
                        ? value
                        : value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean matches(FileContext file) {
        Objects.requireNonNull(file);

        boolean contained = file.getExtensions().stream()
                .map(value -> caseSensitive
                        ? value
                        : value.toLowerCase(Locale.ROOT))
                .anyMatch(extensions::contains);

        return operator.apply(contained);
    }

    public Set<String> getExtensions() {
        return extensions;
    }

    public MembershipOperator getOperator() {
        return operator;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    private static String stripLeadingDot(String value) {
        String result = value;
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        return result;
    }
}
