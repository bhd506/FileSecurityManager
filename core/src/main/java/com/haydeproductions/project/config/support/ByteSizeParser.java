package com.haydeproductions.project.config.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.haydeproductions.project.config.ConfigException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ByteSizeParser {

    private static final Pattern PATTERN = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z]+)?$"
    );

    private ByteSizeParser() {
    }

    public static long parse(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            throw ConfigNodes.error(path, "is required");
        }

        if (node.isIntegralNumber()) {
            long value = node.longValue();
            if (value < 0) {
                throw ConfigNodes.error(path, "cannot be negative");
            }
            return value;
        }

        if (!node.isTextual()) {
            throw ConfigNodes.error(
                    path,
                    "must be a non-negative byte count or size string"
            );
        }

        String raw = node.asText().trim();
        Matcher matcher = PATTERN.matcher(raw);
        if (!matcher.matches()) {
            throw ConfigNodes.error(path, "invalid size: " + raw);
        }

        BigDecimal amount = new BigDecimal(matcher.group(1));
        String unit = matcher.group(2) == null
                ? "B"
                : matcher.group(2).toUpperCase(Locale.ROOT);

        BigInteger multiplier = switch (unit) {
            case "B", "BYTE", "BYTES" -> BigInteger.ONE;
            case "KB" -> BigInteger.valueOf(1_000L);
            case "MB" -> BigInteger.valueOf(1_000_000L);
            case "GB" -> BigInteger.valueOf(1_000_000_000L);
            case "TB" -> BigInteger.valueOf(1_000_000_000_000L);
            case "KIB" -> BigInteger.valueOf(1_024L);
            case "MIB" -> BigInteger.valueOf(1_048_576L);
            case "GIB" -> BigInteger.valueOf(1_073_741_824L);
            case "TIB" -> BigInteger.valueOf(1_099_511_627_776L);
            default -> throw ConfigNodes.error(
                    path,
                    "unsupported size unit: " + unit
            );
        };

        try {
            return amount
                    .multiply(new BigDecimal(multiplier))
                    .toBigIntegerExact()
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new ConfigException(
                    path + " size must resolve to a whole byte count within long range",
                    exception
            );
        }
    }
}
