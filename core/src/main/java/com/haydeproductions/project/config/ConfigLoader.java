package com.haydeproductions.project.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.haydeproductions.mirror.config.ConflictPolicy;
import com.haydeproductions.mirror.config.MirrorMode;
import com.haydeproductions.mirror.config.TransferMode;
import com.haydeproductions.project.config.support.ConfigNames;
import com.haydeproductions.project.config.support.DurationParser;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
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

        List<MirrorDefinition> mirrors = createMirrorDefinitions(
                rawConfig.mirrors
        );

        Set<String> mirrorIds = new HashSet<>();
        for (MirrorDefinition mirror : mirrors) {
            mirrorIds.add(mirror.id());
        }

        RuleConfigCompiler ruleCompiler =
                new RuleConfigCompiler(
                        normalizedRoot,
                        actionServices.withMirrorIds(mirrorIds)
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
        RuntimeConfig runtime = createRuntimeConfig(rawConfig.runtime);

        return new Config(
                normalizedRoot,
                overrideRegistry,
                ruleSets,
                statusApi,
                runtime,
                mirrors
        );
    }


    private static RuntimeConfig createRuntimeConfig(RuntimeRawConfig raw) {
        if (raw == null) {
            return RuntimeConfig.defaults();
        }

        Duration debounce = raw.debounce == null
                ? RuntimeConfig.DEFAULT_DEBOUNCE
                : DurationParser.parse(raw.debounce, "runtime.debounce");
        int workerThreads = raw.workerThreads == null
                ? RuntimeConfig.defaults().workerThreads()
                : raw.workerThreads;
        try {
            return new RuntimeConfig(debounce, workerThreads);
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(exception.getMessage(), exception);
        }
    }

    private static List<MirrorDefinition> createMirrorDefinitions(
            List<MirrorRawConfig> configs
    ) {
        if (configs == null) {
            return List.of();
        }

        List<MirrorDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> caseInsensitiveIds = new HashSet<>();

        for (int index = 0; index < configs.size(); index++) {
            MirrorRawConfig raw = configs.get(index);
            String base = "mirrors[" + index + "]";
            if (raw == null) {
                throw new ConfigException(base + " cannot be null");
            }
            if (raw.id == null || raw.id.isBlank()) {
                throw new ConfigException(base + ".id is required");
            }
            if (!ids.add(raw.id)
                    || !caseInsensitiveIds.add(raw.id.toLowerCase(java.util.Locale.ROOT))) {
                throw new ConfigException("Duplicate mirror id: " + raw.id);
            }
            if (raw.path == null || raw.path.isBlank()) {
                throw new ConfigException(base + ".path is required");
            }

            MirrorMode mode = parseMirrorMode(raw.mode, base + ".mode");
            TransferMode transfer = parseTransferMode(
                    raw.transferMode,
                    base + ".transferMode"
            );
            ConflictPolicy conflict = parseConflictPolicy(
                    raw.conflictPolicy,
                    base + ".conflictPolicy"
            );
            Duration debounce = raw.debounce == null
                    ? Duration.ofMillis(150)
                    : DurationParser.parse(raw.debounce, base + ".debounce");
            int attempts = raw.maxTransferAttempts == null
                    ? 3
                    : raw.maxTransferAttempts;
            boolean persistent = raw.persistentState != null
                    ? raw.persistentState
                    : mode == MirrorMode.BIDIRECTIONAL;

            try {
                result.add(new MirrorDefinition(
                        raw.id,
                        Path.of(raw.path),
                        mode,
                        transfer,
                        conflict,
                        debounce,
                        attempts,
                        persistent
                ));
            } catch (IllegalArgumentException exception) {
                throw new ConfigException(base + ": " + exception.getMessage(), exception);
            }
        }
        return List.copyOf(result);
    }

    private static MirrorMode parseMirrorMode(String value, String path) {
        if (value == null) {
            return MirrorMode.ONE_TIME;
        }
        return switch (ConfigNames.normalize(value)) {
            case "onetime" -> MirrorMode.ONE_TIME;
            case "sourcetomirror" -> MirrorMode.SOURCE_TO_MIRROR;
            case "mirrortosource" -> MirrorMode.MIRROR_TO_SOURCE;
            case "bidirectional" -> MirrorMode.BIDIRECTIONAL;
            default -> throw new ConfigException(path + " unsupported mirror mode: " + value);
        };
    }

    private static TransferMode parseTransferMode(String value, String path) {
        if (value == null) {
            return TransferMode.COPY;
        }
        return switch (ConfigNames.normalize(value)) {
            case "copy" -> TransferMode.COPY;
            case "move" -> TransferMode.MOVE;
            default -> throw new ConfigException(path + " unsupported transfer mode: " + value);
        };
    }

    private static ConflictPolicy parseConflictPolicy(String value, String path) {
        if (value == null) {
            return ConflictPolicy.FAIL;
        }
        return switch (ConfigNames.normalize(value)) {
            case "fail" -> ConflictPolicy.FAIL;
            case "sourcewins" -> ConflictPolicy.SOURCE_WINS;
            case "mirrorwins" -> ConflictPolicy.MIRROR_WINS;
            default -> throw new ConfigException(path + " unsupported conflict policy: " + value);
        };
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
        public RuntimeRawConfig runtime;
        public List<MirrorRawConfig> mirrors;
    }


    private static class RuntimeRawConfig {
        public String debounce;
        public Integer workerThreads;
    }

    private static class MirrorRawConfig {
        public String id;
        public String path;
        public String mode;
        public String transferMode;
        public String conflictPolicy;
        public String debounce;
        public Integer maxTransferAttempts;
        public Boolean persistentState;
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
