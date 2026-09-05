package com.haydeproductions.project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConfigLoader {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper(new YAMLFactory());

    private ConfigLoader() {
    }

    public static Config load(Path configPath, Path root) throws IOException {

        RawConfig rawConfig = OBJECT_MAPPER.readValue(
                configPath.toFile(),
                RawConfig.class
        );

        root = root.toAbsolutePath().normalize();

        Set<Path> overridePaths = createOverridePaths(
                root,
                rawConfig.ruleSets
        );

        OverrideRegistry overrideRegistry =
                new OverrideRegistry(overridePaths);

        List<RuleSet> ruleSets = createRuleSets(
                root,
                overrideRegistry,
                rawConfig.ruleSets
        );

        /*
         * Later:
         *
         * - Load configured Rule definitions
         * - Construct Rule objects
         * - Assign Rules to RuleSets
         */

        return new Config(
                root,
                overrideRegistry,
                ruleSets
        );
    }

    private static Set<Path> createOverridePaths(
            Path root,
            List<RuleSetConfig> configs
    ) {

        Set<Path> overridePaths = new HashSet<>();

        if (configs == null) {
            return overridePaths;
        }

        for (RuleSetConfig config : configs) {

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
            List<RuleSetConfig> configs
    ) {

        List<RuleSet> ruleSets = new ArrayList<>();

        if (configs == null) {
            return ruleSets;
        }

        for (RuleSetConfig config : configs) {

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

            ruleSets.add(builder.build());
        }

        return ruleSets;
    }

    private static Path resolveRuleSetRoot(
            Path root,
            String path
    ) {

        String relativePath =
                path == null || path.isBlank()
                        ? "."
                        : path;

        return root
                .resolve(relativePath)
                .normalize();
    }

    private static class RawConfig {
        public List<RuleSetConfig> ruleSets;
    }

    private static class RuleSetConfig {
        public String path;
        public Integer maxDepth;
        public boolean override;

        /*
         * Later:
         *
         * public List<String> rules;
         */
    }
}