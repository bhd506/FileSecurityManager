package com.haydeproductions.project.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.haydeproductions.project.config.action.ActionConfigCompiler;
import com.haydeproductions.project.config.condition.ConditionConfigCompiler;
import com.haydeproductions.project.config.custom.CustomRuleCompiler;
import com.haydeproductions.project.config.predefined.PredefinedRuleCompiler;
import com.haydeproductions.project.config.support.ConfigNodes;
import com.haydeproductions.project.rule.Rule;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class RuleConfigCompiler {

    private final CustomRuleCompiler customCompiler;
    private final PredefinedRuleCompiler predefinedCompiler;

    public RuleConfigCompiler(
            Path sourceRoot,
            ConfigActionServices actionServices
    ) {
        Objects.requireNonNull(sourceRoot);
        Objects.requireNonNull(actionServices);

        ActionConfigCompiler actionCompiler =
                new ActionConfigCompiler(actionServices);

        ConditionConfigCompiler conditionCompiler =
                new ConditionConfigCompiler(sourceRoot);

        this.customCompiler = new CustomRuleCompiler(
                conditionCompiler,
                actionCompiler
        );

        this.predefinedCompiler = new PredefinedRuleCompiler(
                sourceRoot,
                actionCompiler
        );
    }

    public Rule compile(JsonNode node, String path) {
        Map.Entry<String, JsonNode> entry =
                ConfigNodes.requireSingleField(node, path);

        return switch (entry.getKey()) {
            case "custom" -> customCompiler.compile(
                    entry.getValue(),
                    path + ".custom"
            );
            case "predefined" -> predefinedCompiler.compile(
                    entry.getValue(),
                    path + ".predefined"
            );
            default -> throw ConfigNodes.error(
                    path + "." + entry.getKey(),
                    "rule must be either custom or predefined"
            );
        };
    }
}
