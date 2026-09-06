package com.haydeproductions.project.config.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.haydeproductions.project.config.ConfigActionServices;
import com.haydeproductions.project.config.ConfigException;
import com.haydeproductions.project.config.support.ConfigNames;
import com.haydeproductions.project.config.support.ConfigNodes;
import com.haydeproductions.project.log.LogHandler;
import com.haydeproductions.project.quarantine.QuarantineService;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.action.DeleteAction;
import com.haydeproductions.project.rule.action.FlagAction;
import com.haydeproductions.project.rule.action.QuarantineAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        if (node.isTextual()) {
            return List.of(compile(node.asText(), path));
        }

        ConfigNodes.requireArray(node, path);
        List<Action> actions = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            String itemPath = path + "[" + index + "]";
            actions.add(compile(
                    ConfigNodes.requireText(item, itemPath),
                    itemPath
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
            default -> throw new ConfigException(
                    path + " unsupported action: " + rawName
            );
        };
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
