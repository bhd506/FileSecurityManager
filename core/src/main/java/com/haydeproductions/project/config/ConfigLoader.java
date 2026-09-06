package com.haydeproductions.project.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConfigLoader {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper(new YAMLFactory())
                    .configure(
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            true
                    );

    private ConfigLoader() {
    }

    public static Config load(Path configPath, Path root)
            throws IOException {
        return load(
                configPath,
                root,
                ConfigActionServices.none()
        );
    }

    public static Config load(
            Path configPath,
            Path root,
            ConfigActionServices actionServices
    ) throws IOException {

        Objects.requireNonNull(configPath);
        Objects.requireNonNull(root);
        Objects.requireNonNull(actionServices);

        RawConfig rawConfig = OBJECT_MAPPER.readValue(
                configPath.toFile(),
                RawConfig.class
        );

        if (rawConfig == null) {
            rawConfig = new RawConfig();
        }

        validateVersion(rawConfig.version);

        Path normalizedRoot = root
                .toAbsolutePath()
                .normalize();

        Set<Path> overridePaths = createOverridePaths(
                normalizedRoot,
                rawConfig.ruleSets
        );

        OverrideRegistry overrideRegistry =
                new OverrideRegistry(overridePaths);

        RuleConfigCompiler ruleCompiler =
                new RuleConfigCompiler(
                        normalizedRoot,
                        actionServices
                );

        List<RuleSet> ruleSets = createRuleSets(
                normalizedRoot,
                overrideRegistry,
                rawConfig.ruleSets,
                ruleCompiler
        );

        StatusApiConfig statusApi = createStatusApiConfig(
                rawConfig.statusApi
        );

        return new Config(
                normalizedRoot,
                overrideRegistry,
                ruleSets,
                statusApi
        );
    }


    private static StatusApiConfig createStatusApiConfig(
            StatusApiRawConfig raw
    ) {
        if (raw == null) {
            return StatusApiConfig.disabled();
        }

        String host = raw.host == null
                ? StatusApiConfig.DEFAULT_HOST
                : raw.host;

        int port = raw.port == null
                ? StatusApiConfig.DEFAULT_PORT
                : raw.port;

        try {
            return new StatusApiConfig(
                    raw.enabled,
                    host,
                    port
            );
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(
                    exception.getMessage(),
                    exception
            );
        }
    }

    private static void validateVersion(Integer version) {
        int effectiveVersion = version == null
                ? ConfigSyntax.CURRENT_VERSION
                : version;

        if (effectiveVersion != ConfigSyntax.CURRENT_VERSION) {
            throw new ConfigException(
                    "Unsupported config version " + effectiveVersion
                            + "; supported version is "
                            + ConfigSyntax.CURRENT_VERSION
            );
        }
    }

    private static Set<Path> createOverridePaths(
            Path root,
            List<RuleSetConfig> configs
    ) {

        Set<Path> overridePaths = new HashSet<>();

        if (configs == null) {
            return overridePaths;
        }

        for (int index = 0; index < configs.size(); index++) {
            RuleSetConfig config = requireRuleSetConfig(
                    configs.get(index),
                    index
            );

            if (!config.override) {
                continue;
            }

            Path ruleSetRoot = resolveRuleSetRoot(
                    root,
                    config.path
            );

            overridePaths.add(ruleSetRoot);
        }

        return overridePaths;
    }

    private static List<RuleSet> createRuleSets(
            Path root,
            OverrideRegistry overrideRegistry,
            List<RuleSetConfig> configs,
            RuleConfigCompiler ruleCompiler
    ) {

        List<RuleSet> ruleSets = new ArrayList<>();

        if (configs == null) {
            return ruleSets;
        }

        for (int index = 0; index < configs.size(); index++) {
            RuleSetConfig config = requireRuleSetConfig(
                    configs.get(index),
                    index
            );

            Path ruleSetRoot = resolveRuleSetRoot(
                    root,
                    config.path
            );

            RuleSet.Builder builder =
                    RuleSet.builder(
                            ruleSetRoot,
                            overrideRegistry
                    );

            if (config.maxDepth != null) {
                builder.maxDepth(config.maxDepth);
            }

            if (config.rules != null) {
                for (int ruleIndex = 0;
                     ruleIndex < config.rules.size();
                     ruleIndex++) {

                    Rule rule = ruleCompiler.compile(
                            config.rules.get(ruleIndex),
                            "ruleSets[" + index + "].rules["
                                    + ruleIndex + "]"
                    );
                    builder.rule(rule);
                }
            }

            ruleSets.add(builder.build());
        }

        return List.copyOf(ruleSets);
    }

    private static RuleSetConfig requireRuleSetConfig(
            RuleSetConfig config,
            int index
    ) {
        if (config == null) {
            throw new ConfigException(
                    "ruleSets[" + index + "] cannot be null"
            );
        }
        return config;
    }

    private static Path resolveRuleSetRoot(
            Path root,
            String path
    ) {

        String relativePath =
                path == null || path.isBlank()
                        ? "."
                        : path;

        Path configuredPath = Path.of(relativePath);

        if (configuredPath.isAbsolute()) {
            throw new ConfigException(
                    "RuleSet path must be relative to the configured root: "
                            + relativePath
            );
        }

        Path resolved = root
                .resolve(configuredPath)
                .normalize();

        if (!resolved.startsWith(root)) {
            throw new ConfigException(
                    "RuleSet path escapes the configured root: "
                            + relativePath
            );
        }

        return resolved;
    }

    private static class RawConfig {
        public Integer version;
        public List<RuleSetConfig> ruleSets;
        public StatusApiRawConfig statusApi;
    }

    private static class StatusApiRawConfig {
        public boolean enabled;
        public String host;
        public Integer port;
    }

    private static class RuleSetConfig {
        public String path;
        public Integer maxDepth;
        public boolean override;
        public List<JsonNode> rules;
    }
}
