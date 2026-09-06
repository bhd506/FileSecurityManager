package com.haydeproductions.project.rule.condition.path;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.operator.TextMatchOperator;
import com.haydeproductions.project.rule.condition.support.TextMatcher;

import java.util.Objects;

public final class FileNameCondition implements Condition {

    private final TextMatcher matcher;

    public FileNameCondition(
            TextMatchOperator operator,
            String pattern
    ) {
        this(operator, pattern, true);
    }

    public FileNameCondition(
            TextMatchOperator operator,
            String pattern,
            boolean caseSensitive
    ) {
        this.matcher = new TextMatcher(
                Objects.requireNonNull(operator),
                Objects.requireNonNull(pattern),
                caseSensitive
        );
    }

    @Override
    public boolean matches(FileContext file) {
        return matcher.matches(
                Objects.requireNonNull(file).getFileName()
        );
    }
}
