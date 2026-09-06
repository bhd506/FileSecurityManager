package com.haydeproductions.project.config.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.haydeproductions.project.config.ConfigActionServices;
import com.haydeproductions.project.config.ConfigException;
import com.haydeproductions.project.config.support.ConfigNames;
import com.haydeproductions.project.config.support.ConfigNodes;
import com.haydeproductions.project.log.LogHandler;
import com.haydeproductions.project.quarantine.QuarantineService;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.action.AllowMirrorAction;
import com.haydeproductions.project.rule.action.DeleteAction;
import com.haydeproductions.project.rule.action.DenyMirrorAction;
import com.haydeproductions.project.rule.action.FlagAction;
import com.haydeproductions.project.rule.action.QuarantineAction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ActionConfigCompiler {

    private final ConfigActionServices services;

    public ActionConfigCompiler(ConfigActionServices services) {
        this.services = Objects.requireNonNull(services);
    }

    public List<Action> compileList(
            JsonNode node,
            String path,
            List<String> defaultActions
    ) {
        Objects.requireNonNull(defaultActions);

        if (node == null || node.isNull()) {
            return compileNames(defaultActions, path);
        }

        if (!node.isArray()) {
            return List.of(compileNode(node, path));
        }

        List<Action> actions = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            actions.add(compileNode(
                    node.get(index),
                    path + "[" + index + "]"
            ));
        }
        return List.copyOf(actions);
    }

    public Action compile(String rawName, String path) {
        String name = ConfigNames.normalize(
                Objects.requireNonNull(rawName)
        );

        return switch (name) {
            case "flag" -> new FlagAction();
            case "delete" -> new DeleteAction(requireLogHandler(path));
            case "quarantine" -> new QuarantineAction(
                    requireQuarantineService(path),
                    requireLogHandler(path)
            );
            case "allowmirror", "denymirror" -> throw new ConfigException(
                    path + " " + rawName + " requires one or more mirror ids"
            );
            default -> throw new ConfigException(
                    path + " unsupported action: " + rawName
            );
        };
    }

    private Action compileNode(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            throw new ConfigException(path + " action cannot be null");
        }
        if (node.isTextual()) {
            return compile(node.asText(), path);
        }
        ConfigNodes.requireObject(node, path);
        if (node.size() != 1) {
            throw new ConfigException(
                    path + " structured action must contain exactly one action name"
            );
        }

        String rawName = node.fieldNames().next();
        JsonNode value = node.get(rawName);
        String name = ConfigNames.normalize(rawName);

        return switch (name) {
            case "allowmirror" -> new AllowMirrorAction(
                    mirrorIds(value, path + "." + rawName)
            );
            case "denymirror" -> new DenyMirrorAction(
                    mirrorIds(value, path + "." + rawName)
            );
            default -> throw new ConfigException(
                    path + " unsupported structured action: " + rawName
            );
        };
    }

    private Set<String> mirrorIds(JsonNode node, String path) {
        Set<String> ids = new LinkedHashSet<>();

        if (node != null && node.isTextual()) {
            ids.add(ConfigNodes.requireText(node, path));
        } else {
            ConfigNodes.requireArray(node, path);
            if (node.isEmpty()) {
                throw new ConfigException(path + " requires at least one mirror id");
            }
            for (int index = 0; index < node.size(); index++) {
                ids.add(ConfigNodes.requireText(
                        node.get(index),
                        path + "[" + index + "]"
                ));
            }
        }

        for (String id : ids) {
            if (!services.getMirrorIds().contains(id)) {
                throw new ConfigException(
                        path + " references unknown mirror id: " + id
                );
            }
        }
        return Set.copyOf(ids);
    }

    private List<Action> compileNames(
            List<String> names,
            String path
    ) {
        List<Action> result = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            result.add(compile(names.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(result);
    }

    private LogHandler requireLogHandler(String path) {
        return services.getLogHandler().orElseThrow(
                () -> new ConfigException(
                        path + " requires a configured LogHandler"
                )
        );
    }

    private QuarantineService requireQuarantineService(String path) {
        return services.getQuarantineService().orElseThrow(
                () -> new ConfigException(
                        path + " requires a configured QuarantineService"
                )
        );
    }
}
