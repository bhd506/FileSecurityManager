package com.haydeproductions.project.config.custom;

import com.fasterxml.jackson.databind.JsonNode;
import com.haydeproductions.project.config.action.ActionConfigCompiler;
import com.haydeproductions.project.config.condition.ConditionConfigCompiler;
import com.haydeproductions.project.config.support.ConfigNodes;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.condition.Condition;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CustomRuleCompiler {

    private final ConditionConfigCompiler conditionCompiler;
    private final ActionConfigCompiler actionCompiler;

    public CustomRuleCompiler(
            ConditionConfigCompiler conditionCompiler,
            ActionConfigCompiler actionCompiler
    ) {
        this.conditionCompiler = Objects.requireNonNull(conditionCompiler);
        this.actionCompiler = Objects.requireNonNull(actionCompiler);
    }

    public Rule compile(JsonNode node, String path) {
        ConfigNodes.requireOnlyFields(
                node,
                Set.of("condition", "onMatch", "onNoMatch"),
                path
        );

        Condition condition = conditionCompiler.compile(
                ConfigNodes.requireField(node, "condition", path),
                path + ".condition"
        );

        List<Action> onMatch = actionCompiler.compileList(
                node.get("onMatch"),
                path + ".onMatch",
                List.of()
        );

        List<Action> onNoMatch = actionCompiler.compileList(
                node.get("onNoMatch"),
                path + ".onNoMatch",
                List.of()
        );

        return new Rule(condition, onMatch, onNoMatch);
    }
}
