package com.haydeproductions.project.config.support;

import com.haydeproductions.project.config.ConfigException;
import com.haydeproductions.project.rule.condition.operator.LongComparisonOperator;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;
import com.haydeproductions.project.rule.condition.operator.TextMatchOperator;
import com.haydeproductions.project.rule.condition.operator.TimeComparisonOperator;

import java.util.Locale;

public final class ConfigNames {

    private ConfigNames() {
    }

    public static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }

    public static MembershipOperator membership(String value, String path) {
        return switch (normalize(value)) {
            case "in" -> MembershipOperator.IN;
            case "notin" -> MembershipOperator.NOT_IN;
            default -> throw new ConfigException(
                    path + " unsupported membership operator: " + value
            );
        };
    }

    public static TextMatchOperator text(String value, String path) {
        return switch (normalize(value)) {
            case "equals", "equal" -> TextMatchOperator.EQUALS;
            case "notequals", "notequal" -> TextMatchOperator.NOT_EQUALS;
            case "startswith" -> TextMatchOperator.STARTS_WITH;
            case "endswith" -> TextMatchOperator.ENDS_WITH;
            case "contains" -> TextMatchOperator.CONTAINS;
            case "glob" -> TextMatchOperator.GLOB;
            case "regex" -> TextMatchOperator.REGEX;
            default -> throw new ConfigException(
                    path + " unsupported text operator: " + value
            );
        };
    }

    public static LongComparisonOperator longComparison(
            String value,
            String path
    ) {
        return switch (normalize(value)) {
            case "equal", "equals" -> LongComparisonOperator.EQUAL;
            case "notequal", "notequals" -> LongComparisonOperator.NOT_EQUAL;
            case "lessthan" -> LongComparisonOperator.LESS_THAN;
            case "lessthanorequal", "lessthanorequals" ->
                    LongComparisonOperator.LESS_THAN_OR_EQUAL;
            case "greaterthan" -> LongComparisonOperator.GREATER_THAN;
            case "greaterthanorequal", "greaterthanorequals" ->
                    LongComparisonOperator.GREATER_THAN_OR_EQUAL;
            default -> throw new ConfigException(
                    path + " unsupported numeric operator: " + value
            );
        };
    }

    public static TimeComparisonOperator timeComparison(
            String value,
            String path
    ) {
        return switch (normalize(value)) {
            case "before" -> TimeComparisonOperator.BEFORE;
            case "beforeorequal", "beforeorequals" ->
                    TimeComparisonOperator.BEFORE_OR_EQUAL;
            case "equal", "equals" -> TimeComparisonOperator.EQUAL;
            case "afterorequal", "afterorequals" ->
                    TimeComparisonOperator.AFTER_OR_EQUAL;
            case "after" -> TimeComparisonOperator.AFTER;
            default -> throw new ConfigException(
                    path + " unsupported time operator: " + value
            );
        };
    }
}
