package com.haydeproductions.project.config.predefined;

import com.fasterxml.jackson.databind.JsonNode;
import com.haydeproductions.project.config.ConfigException;
import com.haydeproductions.project.config.action.ActionConfigCompiler;
import com.haydeproductions.project.config.support.ByteSizeParser;
import com.haydeproductions.project.config.support.ConfigNames;
import com.haydeproductions.project.config.support.ConfigNodes;
import com.haydeproductions.project.config.support.DurationParser;
import com.haydeproductions.project.file.FileSignature;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.content.FileSignatureCondition;
import com.haydeproductions.project.rule.condition.content.HashCondition;
import com.haydeproductions.project.rule.condition.content.MimeTypeCondition;
import com.haydeproductions.project.rule.condition.logic.OrCondition;
import com.haydeproductions.project.rule.condition.metadata.ModifiedTimeCondition;
import com.haydeproductions.project.rule.condition.metadata.SizeCondition;
import com.haydeproductions.project.rule.condition.operator.LongComparisonOperator;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;
import com.haydeproductions.project.rule.condition.operator.TextMatchOperator;
import com.haydeproductions.project.rule.condition.path.ExtensionCondition;
import com.haydeproductions.project.rule.condition.path.FileNameCondition;
import com.haydeproductions.project.rule.condition.path.PathCondition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PredefinedRuleCompiler {

    private static final Set<String> COMMON_FIELDS = Set.of(
            "type", "onMatch", "onNoMatch"
    );

    private final Path sourceRoot;
    private final ActionConfigCompiler actionCompiler;

    public PredefinedRuleCompiler(
            Path sourceRoot,
            ActionConfigCompiler actionCompiler
    ) {
        this.sourceRoot = Objects.requireNonNull(sourceRoot)
                .toAbsolutePath()
                .normalize();
        this.actionCompiler = Objects.requireNonNull(actionCompiler);
    }

    public Rule compile(JsonNode node, String path) {
        ConfigNodes.requireObject(node, path);

        String rawType = ConfigNodes.requireText(
                ConfigNodes.requireField(node, "type", path),
                path + ".type"
        );

        String type = ConfigNames.normalize(rawType);

        Condition condition = switch (type) {
            case "blockextensions" -> extensions(node, path, MembershipOperator.IN);
            case "allowextensions" -> extensions(node, path, MembershipOperator.NOT_IN);
            case "blockfilenames" -> fileNames(node, path);
            case "blockpaths" -> paths(node, path);
            case "maxfilesize" -> maxFileSize(node, path);
            case "minfilesize" -> minFileSize(node, path);
            case "blockhashes" -> hashes(node, path, MembershipOperator.IN);
            case "allowhashes" -> hashes(node, path, MembershipOperator.NOT_IN);
            case "blockmimetypes" -> mimeTypes(node, path, MembershipOperator.IN);
            case "allowmimetypes" -> mimeTypes(node, path, MembershipOperator.NOT_IN);
            case "blocksignatures" -> signatures(node, path, MembershipOperator.IN);
            case "allowsignatures" -> signatures(node, path, MembershipOperator.NOT_IN);
            case "olderthan" -> olderThan(node, path);
            case "newerthan" -> newerThan(node, path);
            default -> throw new ConfigException(
                    path + ".type unsupported predefined rule: " + rawType
            );
        };

        List<Action> onMatch = actionCompiler.compileList(
                node.get("onMatch"),
                path + ".onMatch",
                List.of("flag")
        );

        List<Action> onNoMatch = actionCompiler.compileList(
                node.get("onNoMatch"),
                path + ".onNoMatch",
                List.of()
        );

        return new Rule(condition, onMatch, onNoMatch);
    }

    private Condition extensions(
            JsonNode node,
            String path,
            MembershipOperator operator
    ) {
        requireFields(node, path, "extensions", "caseSensitive");

        return new ExtensionCondition(
                ConfigNodes.requireStringList(
                        ConfigNodes.requireField(node, "extensions", path),
                        path + ".extensions"
                ),
                operator,
                ConfigNodes.optionalBoolean(
                        node,
                        "caseSensitive",
                        false,
                        path
                )
        );
    }

    private Condition fileNames(JsonNode node, String path) {
        requireFields(
                node,
                path,
                "patterns",
                "operator",
                "caseSensitive"
        );

        List<String> patterns = ConfigNodes.requireStringList(
                ConfigNodes.requireField(node, "patterns", path),
                path + ".patterns"
        );

        TextMatchOperator operator = ConfigNames.text(
                ConfigNodes.optionalText(
                        node,
                        "operator",
                        "glob",
                        path
                ),
                path + ".operator"
        );

        boolean caseSensitive = ConfigNodes.optionalBoolean(
                node,
                "caseSensitive",
                true,
                path
        );

        List<Condition> conditions = patterns.stream()
                .map(pattern -> (Condition) new FileNameCondition(
                        operator,
                        pattern,
                        caseSensitive
                ))
                .toList();

        return anyOf(conditions);
    }

    private Condition paths(JsonNode node, String path) {
        requireFields(
                node,
                path,
                "patterns",
                "operator",
                "caseSensitive"
        );

        List<String> patterns = ConfigNodes.requireStringList(
                ConfigNodes.requireField(node, "patterns", path),
                path + ".patterns"
        );

        TextMatchOperator operator = ConfigNames.text(
                ConfigNodes.optionalText(
                        node,
                        "operator",
                        "glob",
                        path
                ),
                path + ".operator"
        );

        boolean caseSensitive = ConfigNodes.optionalBoolean(
                node,
                "caseSensitive",
                true,
                path
        );

        List<Condition> conditions = patterns.stream()
                .map(pattern -> (Condition) new PathCondition(
                        sourceRoot,
                        operator,
                        pattern,
                        caseSensitive
                ))
                .toList();

        return anyOf(conditions);
    }

    private Condition maxFileSize(JsonNode node, String path) {
        requireFields(node, path, "max");
        return new SizeCondition(
                LongComparisonOperator.GREATER_THAN,
                ByteSizeParser.parse(
                        ConfigNodes.requireField(node, "max", path),
                        path + ".max"
                )
        );
    }

    private Condition minFileSize(JsonNode node, String path) {
        requireFields(node, path, "min");
        return new SizeCondition(
                LongComparisonOperator.LESS_THAN,
                ByteSizeParser.parse(
                        ConfigNodes.requireField(node, "min", path),
                        path + ".min"
                )
        );
    }

    private Condition hashes(
            JsonNode node,
            String path,
            MembershipOperator operator
    ) {
        requireFields(node, path, "hashes");
        return new HashCondition(
                ConfigNodes.requireStringList(
                        ConfigNodes.requireField(node, "hashes", path),
                        path + ".hashes"
                ),
                operator
        );
    }

    private Condition mimeTypes(
            JsonNode node,
            String path,
            MembershipOperator operator
    ) {
        requireFields(node, path, "mimeTypes");
        return new MimeTypeCondition(
                ConfigNodes.requireStringList(
                        ConfigNodes.requireField(node, "mimeTypes", path),
                        path + ".mimeTypes"
                ),
                operator
        );
    }

    private Condition signatures(
            JsonNode node,
            String path,
            MembershipOperator operator
    ) {
        requireFields(node, path, "signatures");
        List<String> values = ConfigNodes.requireStringList(
                ConfigNodes.requireField(node, "signatures", path),
                path + ".signatures"
        );

        List<FileSignature> signatures = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            signatures.add(parseSignature(
                    values.get(index),
                    path + ".signatures[" + index + "]"
            ));
        }

        return new FileSignatureCondition(signatures, operator);
    }

    private Condition olderThan(JsonNode node, String path) {
        requireFields(node, path, "age");
        return ModifiedTimeCondition.olderThan(
                DurationParser.parse(
                        ConfigNodes.requireText(
                                ConfigNodes.requireField(node, "age", path),
                                path + ".age"
                        ),
                        path + ".age"
                )
        );
    }

    private Condition newerThan(JsonNode node, String path) {
        requireFields(node, path, "age");
        return ModifiedTimeCondition.newerThan(
                DurationParser.parse(
                        ConfigNodes.requireText(
                                ConfigNodes.requireField(node, "age", path),
                                path + ".age"
                        ),
                        path + ".age"
                )
        );
    }

    private Condition anyOf(List<Condition> conditions) {
        if (conditions.size() == 1) {
            return conditions.get(0);
        }
        return new OrCondition(conditions);
    }

    private void requireFields(
            JsonNode node,
            String path,
            String... specificFields
    ) {
        Set<String> allowed = new java.util.HashSet<>(COMMON_FIELDS);
        allowed.addAll(List.of(specificFields));
        ConfigNodes.requireOnlyFields(node, Set.copyOf(allowed), path);
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
