package com.haydeproductions.project.config.support;

import com.haydeproductions.project.config.ConfigException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern SHORT = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)\\s*(ms|s|m|h|d|w)$",
            Pattern.CASE_INSENSITIVE
    );

    private DurationParser() {
    }

    public static Duration parse(String raw, String path) {
        if (raw == null || raw.isBlank()) {
            throw new ConfigException(path + " duration cannot be blank");
        }

        String value = raw.trim();

        if (value.regionMatches(true, 0, "P", 0, 1)) {
            try {
                Duration result = Duration.parse(value.toUpperCase(Locale.ROOT));
                if (result.isNegative()) {
                    throw new ConfigException(path + " duration cannot be negative");
                }
                return result;
            } catch (DateTimeParseException exception) {
                throw new ConfigException(
                        path + " invalid ISO-8601 duration: " + raw,
                        exception
                );
            }
        }

        Matcher matcher = SHORT.matcher(value);
        if (!matcher.matches()) {
            throw new ConfigException(
                    path + " invalid duration: " + raw
                            + " (use e.g. 150ms, 30s, 5m, 2h, 7d, 2w or ISO-8601)"
            );
        }

        BigDecimal amount = new BigDecimal(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);

        BigDecimal nanosPerUnit = switch (unit) {
            case "ms" -> BigDecimal.valueOf(1_000_000L);
            case "s" -> BigDecimal.valueOf(1_000_000_000L);
            case "m" -> BigDecimal.valueOf(60_000_000_000L);
            case "h" -> BigDecimal.valueOf(3_600_000_000_000L);
            case "d" -> BigDecimal.valueOf(86_400_000_000_000L);
            case "w" -> BigDecimal.valueOf(604_800_000_000_000L);
            default -> throw new AssertionError(unit);
        };

        try {
            return Duration.ofNanos(
                    amount.multiply(nanosPerUnit)
                            .toBigIntegerExact()
                            .longValueExact()
            );
        } catch (ArithmeticException exception) {
            throw new ConfigException(
                    path + " duration must resolve to a whole nanosecond value within long range",
                    exception
            );
        }
    }
}
