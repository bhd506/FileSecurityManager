package com.haydeproductions.project.config.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.haydeproductions.project.config.ConfigException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConfigNodes {

    private ConfigNodes() {
    }

    public static JsonNode requireObject(JsonNode node, String path) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw error(path, "must be an object");
        }
        return node;
    }

    public static JsonNode requireArray(JsonNode node, String path) {
        if (node == null || node.isNull() || !node.isArray()) {
            throw error(path, "must be an array");
        }
        return node;
    }

    public static JsonNode requireField(
            JsonNode object,
            String field,
            String path
    ) {
        JsonNode value = requireObject(object, path).get(field);
        if (value == null || value.isNull()) {
            throw error(path + "." + field, "is required");
        }
        return value;
    }

    public static String requireText(JsonNode node, String path) {
        if (node == null || !node.isTextual()) {
            throw error(path, "must be a string");
        }

        String value = node.asText();
        if (value.isBlank()) {
            throw error(path, "cannot be blank");
        }
        return value;
    }

    public static String optionalText(
            JsonNode object,
            String field,
            String defaultValue,
            String path
    ) {
        JsonNode value = requireObject(object, path).get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return requireText(value, path + "." + field);
    }

    public static boolean optionalBoolean(
            JsonNode object,
            String field,
            boolean defaultValue,
            String path
    ) {
        JsonNode value = requireObject(object, path).get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw error(path + "." + field, "must be true or false");
        }
        return value.asBoolean();
    }

    public static List<String> requireStringList(
            JsonNode node,
            String path
    ) {
        Objects.requireNonNull(path);

        if (node == null || node.isNull()) {
            throw error(path, "is required");
        }

        if (node.isTextual()) {
            return List.of(requireText(node, path));
        }

        if (!node.isArray()) {
            throw error(path, "must be a string or array of strings");
        }

        if (node.size() == 0) {
            throw error(path, "cannot be empty");
        }

        List<String> result = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            result.add(requireText(node.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(result);
    }

    public static void requireOnlyFields(
            JsonNode object,
            Set<String> allowed,
            String path
    ) {
        requireObject(object, path);
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw error(
                        path + "." + name,
                        "is not a supported field"
                );
            }
        }
    }

    public static Map.Entry<String, JsonNode> requireSingleField(
            JsonNode object,
            String path
    ) {
        requireObject(object, path);
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();

        if (!fields.hasNext()) {
            throw error(path, "must contain exactly one condition type");
        }

        Map.Entry<String, JsonNode> first = fields.next();
        if (fields.hasNext()) {
            throw error(path, "must contain exactly one condition type");
        }
        return first;
    }

    public static ConfigException error(String path, String message) {
        return new ConfigException(path + " " + message);
    }
}
