package com.haydeproductions.project.config.condition;

import com.fasterxml.jackson.databind.JsonNode;
import com.haydeproductions.project.config.ConfigException;
import com.haydeproductions.project.config.support.ByteSizeParser;
import com.haydeproductions.project.config.support.ConfigNames;
import com.haydeproductions.project.config.support.ConfigNodes;
import com.haydeproductions.project.config.support.DurationParser;
import com.haydeproductions.project.file.FileSignature;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.FalseCondition;
import com.haydeproductions.project.rule.condition.TrueCondition;
import com.haydeproductions.project.rule.condition.content.FileSignatureCondition;
import com.haydeproductions.project.rule.condition.content.HashCondition;
import com.haydeproductions.project.rule.condition.content.MimeTypeCondition;
import com.haydeproductions.project.rule.condition.logic.AndCondition;
import com.haydeproductions.project.rule.condition.logic.NotCondition;
import com.haydeproductions.project.rule.condition.logic.OrCondition;
import com.haydeproductions.project.rule.condition.metadata.ModifiedTimeCondition;
import com.haydeproductions.project.rule.condition.metadata.SizeCondition;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;
import com.haydeproductions.project.rule.condition.operator.TextMatchOperator;
import com.haydeproductions.project.rule.condition.operator.TimeComparisonOperator;
import com.haydeproductions.project.rule.condition.path.ExtensionCondition;
import com.haydeproductions.project.rule.condition.path.FileNameCondition;
import com.haydeproductions.project.rule.condition.path.PathCondition;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConditionConfigCompiler {

    private static final Set<String> MEMBERSHIP_FIELDS = Set.of(
            "values", "operator", "caseSensitive"
    );

    private final Path sourceRoot;

    public ConditionConfigCompiler(Path sourceRoot) {
        this.sourceRoot = Objects.requireNonNull(sourceRoot)
                .toAbsolutePath()
                .normalize();
    }

    public Condition compile(JsonNode node, String path) {
        Objects.requireNonNull(path);

        if (node == null || node.isNull()) {
            throw ConfigNodes.error(path, "condition is required");
        }

        if (node.isBoolean()) {
            return node.asBoolean()
                    ? new TrueCondition()
                    : new FalseCondition();
        }

        Map.Entry<String, JsonNode> entry =
                ConfigNodes.requireSingleField(node, path);

        String type = entry.getKey();
        JsonNode value = entry.getValue();
        String valuePath = path + "." + type;

        try {
            return switch (type) {
                case "and" -> compileAnd(value, valuePath);
                case "or" -> compileOr(value, valuePath);
                case "not" -> new NotCondition(compile(value, valuePath));
                case "extension" -> compileExtension(value, valuePath);
                case "fileName" -> compileFileName(value, valuePath);
                case "path" -> compilePath(value, valuePath);
                case "size" -> compileSize(value, valuePath);
                case "modifiedTime" -> compileModifiedTime(value, valuePath);
                case "hash" -> compileHash(value, valuePath);
                case "mimeType" -> compileMimeType(value, valuePath);
                case "fileSignature" -> compileFileSignature(value, valuePath);
                default -> throw ConfigNodes.error(
                        valuePath,
                        "is not a supported condition type"
                );
            };
        } catch (ConfigException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(
                    valuePath + " invalid condition: " + exception.getMessage(),
                    exception
            );
        }
    }

    private Condition compileAnd(JsonNode node, String path) {
        return new AndCondition(compileChildren(node, path));
    }

    private Condition compileOr(JsonNode node, String path) {
        return new OrCondition(compileChildren(node, path));
    }

    private List<Condition> compileChildren(JsonNode node, String path) {
        ConfigNodes.requireArray(node, path);
        if (node.size() == 0) {
            throw ConfigNodes.error(path, "cannot be empty");
        }

        List<Condition> conditions = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            conditions.add(compile(node.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(conditions);
    }

    private Condition compileExtension(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(node, MEMBERSHIP_FIELDS, path);

        List<String> values = ConfigNodes.requireStringList(
                ConfigNodes.requireField(node, "values", path),
                path + ".values"
        );

        MembershipOperator operator = membershipOperator(node, path);
        boolean caseSensitive = ConfigNodes.optionalBoolean(
                node,
                "caseSensitive",
                false,
                path
        );

        return new ExtensionCondition(values, operator, caseSensitive);
    }

    private Condition compileFileName(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(
                node,
                Set.of("operator", "pattern", "caseSensitive"),
                path
        );

        TextMatchOperator operator = ConfigNames.text(
                ConfigNodes.requireText(
                        ConfigNodes.requireField(node, "operator", path),
                        path + ".operator"
                ),
                path + ".operator"
        );

        String pattern = ConfigNodes.requireText(
                ConfigNodes.requireField(node, "pattern", path),
                path + ".pattern"
        );

        boolean caseSensitive = ConfigNodes.optionalBoolean(
                node,
                "caseSensitive",
                true,
                path
        );

        return new FileNameCondition(operator, pattern, caseSensitive);
    }

    private Condition compilePath(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(
                node,
                Set.of("operator", "pattern", "caseSensitive"),
                path
        );

        TextMatchOperator operator = ConfigNames.text(
                ConfigNodes.requireText(
                        ConfigNodes.requireField(node, "operator", path),
                        path + ".operator"
                ),
                path + ".operator"
        );

        String pattern = ConfigNodes.requireText(
                ConfigNodes.requireField(node, "pattern", path),
                path + ".pattern"
        );

        boolean caseSensitive = ConfigNodes.optionalBoolean(
                node,
                "caseSensitive",
                true,
                path
        );

        return new PathCondition(
                sourceRoot,
                operator,
                pattern,
                caseSensitive
        );
    }

    private Condition compileSize(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(
                node,
                Set.of("operator", "value", "betweenInclusive"),
                path
        );

        JsonNode between = node.get("betweenInclusive");
        boolean hasBetween = between != null && !between.isNull();
        boolean hasOperator = node.get("operator") != null
                && !node.get("operator").isNull();
        boolean hasValue = node.get("value") != null
                && !node.get("value").isNull();

        if (hasBetween) {
            if (hasOperator || hasValue) {
                throw ConfigNodes.error(
                        path,
                        "cannot combine betweenInclusive with operator/value"
                );
            }

            ConfigNodes.requireArray(between, path + ".betweenInclusive");
            if (between.size() != 2) {
                throw ConfigNodes.error(
                        path + ".betweenInclusive",
                        "must contain exactly [lower, upper]"
                );
            }

            long lower = ByteSizeParser.parse(
                    between.get(0),
                    path + ".betweenInclusive[0]"
            );
            long upper = ByteSizeParser.parse(
                    between.get(1),
                    path + ".betweenInclusive[1]"
            );
            return SizeCondition.betweenInclusive(lower, upper);
        }

        if (!hasOperator || !hasValue) {
            throw ConfigNodes.error(
                    path,
                    "requires either betweenInclusive or both operator and value"
            );
        }

        String operatorText = ConfigNodes.requireText(
                node.get("operator"),
                path + ".operator"
        );

        return new SizeCondition(
                ConfigNames.longComparison(
                        operatorText,
                        path + ".operator"
                ),
                ByteSizeParser.parse(node.get("value"), path + ".value")
        );
    }

    private Condition compileModifiedTime(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(
                node,
                Set.of(
                        "before",
                        "beforeOrEqual",
                        "equal",
                        "afterOrEqual",
                        "after",
                        "olderThan",
                        "newerThan"
                ),
                path
        );

        Map.Entry<String, JsonNode> entry =
                ConfigNodes.requireSingleField(node, path);

        String field = entry.getKey();
        String fieldPath = path + "." + field;
        String raw = ConfigNodes.requireText(entry.getValue(), fieldPath);

        return switch (field) {
            case "olderThan" -> ModifiedTimeCondition.olderThan(
                    DurationParser.parse(raw, fieldPath)
            );
            case "newerThan" -> ModifiedTimeCondition.newerThan(
                    DurationParser.parse(raw, fieldPath)
            );
            case "before" -> absoluteTime(
                    TimeComparisonOperator.BEFORE,
                    raw,
                    fieldPath
            );
            case "beforeOrEqual" -> absoluteTime(
                    TimeComparisonOperator.BEFORE_OR_EQUAL,
                    raw,
                    fieldPath
            );
            case "equal" -> absoluteTime(
                    TimeComparisonOperator.EQUAL,
                    raw,
                    fieldPath
            );
            case "afterOrEqual" -> absoluteTime(
                    TimeComparisonOperator.AFTER_OR_EQUAL,
                    raw,
                    fieldPath
            );
            case "after" -> absoluteTime(
                    TimeComparisonOperator.AFTER,
                    raw,
                    fieldPath
            );
            default -> throw new AssertionError(field);
        };
    }

    private Condition absoluteTime(
            TimeComparisonOperator operator,
            String raw,
            String path
    ) {
        try {
            return new ModifiedTimeCondition(
                    operator,
                    Instant.parse(raw)
            );
        } catch (DateTimeParseException exception) {
            throw new ConfigException(
                    path + " must be an ISO-8601 instant such as 2026-01-01T00:00:00Z",
                    exception
            );
        }
    }

    private Condition compileHash(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(
                node,
                Set.of("values", "operator"),
                path
        );

        return new HashCondition(
                ConfigNodes.requireStringList(
                        ConfigNodes.requireField(node, "values", path),
                        path + ".values"
                ),
                membershipOperator(node, path)
        );
    }

    private Condition compileMimeType(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(
                node,
                Set.of("patterns", "operator"),
                path
        );

        return new MimeTypeCondition(
                ConfigNodes.requireStringList(
                        ConfigNodes.requireField(node, "patterns", path),
                        path + ".patterns"
                ),
                membershipOperator(node, path)
        );
    }

    private Condition compileFileSignature(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(
                node,
                Set.of("values", "operator"),
                path
        );

        List<String> raw = ConfigNodes.requireStringList(
                ConfigNodes.requireField(node, "values", path),
                path + ".values"
        );

        List<FileSignature> signatures = new ArrayList<>();
        for (int index = 0; index < raw.size(); index++) {
            signatures.add(parseSignature(
                    raw.get(index),
                    path + ".values[" + index + "]"
            ));
        }

        return new FileSignatureCondition(
                signatures,
                membershipOperator(node, path)
        );
    }

    private MembershipOperator membershipOperator(
            JsonNode node,
            String path
    ) {
        String raw = ConfigNodes.optionalText(
                node,
                "operator",
                "in",
                path
        );
        return ConfigNames.membership(raw, path + ".operator");
    }

    private FileSignature parseSignature(String raw, String path) {
        String normalized = ConfigNames.normalize(raw);
        for (FileSignature signature : FileSignature.values()) {
            if (ConfigNames.normalize(signature.name()).equals(normalized)) {
                return signature;
            }
        }

        throw ConfigNodes.error(
                path,
                "unsupported file signature: " + raw
        );
    }
}
