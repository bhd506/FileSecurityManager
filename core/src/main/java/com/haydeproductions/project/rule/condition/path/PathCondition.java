package com.haydeproductions.project.rule.condition.path;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.operator.TextMatchOperator;
import com.haydeproductions.project.rule.condition.support.TextMatcher;

import java.nio.file.Path;
import java.util.Objects;

public final class PathCondition implements Condition {

    private final Path baseRoot;
    private final TextMatcher matcher;

    public PathCondition(
            Path baseRoot,
            TextMatchOperator operator,
            String pattern
    ) {
        this(baseRoot, operator, pattern, true);
    }

    public PathCondition(
            Path baseRoot,
            TextMatchOperator operator,
            String pattern,
            boolean caseSensitive
    ) {
        this.baseRoot = Objects.requireNonNull(baseRoot)
                .toAbsolutePath()
                .normalize();

        this.matcher = new TextMatcher(
                Objects.requireNonNull(operator),
                Objects.requireNonNull(pattern),
                caseSensitive
        );
    }

    @Override
    public boolean matches(FileContext file) {
        Path target = Objects.requireNonNull(file)
                .getNormalizedPath();

        if (!target.startsWith(baseRoot)) {
            return false;
        }

        String relative = baseRoot
                .relativize(target)
                .toString()
                .replace('\\', '/');

        return matcher.matches(relative);
    }

    public Path getBaseRoot() {
        return baseRoot;
    }
}
