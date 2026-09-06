package com.haydeproductions.project.rule.condition.support;

import com.haydeproductions.project.rule.condition.operator.TextMatchOperator;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class TextMatcher {

    private final TextMatchOperator operator;
    private final String patternText;
    private final boolean caseSensitive;
    private final Pattern compiledPattern;

    public TextMatcher(
            TextMatchOperator operator,
            String patternText,
            boolean caseSensitive
    ) {
        this.operator = Objects.requireNonNull(operator);
        this.patternText = Objects.requireNonNull(patternText);
        this.caseSensitive = caseSensitive;
        this.compiledPattern = compileIfNeeded();
    }

    public boolean matches(String value) {
        Objects.requireNonNull(value);

        String left = caseSensitive
                ? value
                : value.toLowerCase(Locale.ROOT);

        String right = caseSensitive
                ? patternText
                : patternText.toLowerCase(Locale.ROOT);

        return switch (operator) {
            case EQUALS -> left.equals(right);
            case NOT_EQUALS -> !left.equals(right);
            case STARTS_WITH -> left.startsWith(right);
            case ENDS_WITH -> left.endsWith(right);
            case CONTAINS -> left.contains(right);
            case GLOB, REGEX -> compiledPattern.matcher(value).matches();
        };
    }

    private Pattern compileIfNeeded() {
        int flags = caseSensitive
                ? 0
                : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

        try {
            return switch (operator) {
                case GLOB -> Pattern.compile(globToRegex(patternText), flags);
                case REGEX -> Pattern.compile(patternText, flags);
                default -> null;
            };
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(
                    "Invalid " + operator + " pattern: " + patternText,
                    exception
            );
        }
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < glob.length(); i++) {
            char current = glob.charAt(i);

            if (current == '*') {
                boolean doubleStar = i + 1 < glob.length()
                        && glob.charAt(i + 1) == '*';

                if (doubleStar) {
                    i++;

                    boolean followedBySlash = i + 1 < glob.length()
                            && glob.charAt(i + 1) == '/';

                    if (followedBySlash) {
                        i++;
                        regex.append("(?:.*/)?");
                    } else {
                        regex.append(".*");
                    }
                } else {
                    regex.append("[^/]*");
                }

                continue;
            }

            if (current == '?') {
                regex.append("[^/]");
                continue;
            }

            if ("\\.^$|()[]{}+".indexOf(current) >= 0) {
                regex.append('\\');
            }

            regex.append(current);
        }

        regex.append('$');
        return regex.toString();
    }
}
