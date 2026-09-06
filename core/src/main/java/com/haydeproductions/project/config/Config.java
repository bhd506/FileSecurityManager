package com.haydeproductions.project.config;

import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class Config {

    private final Path root;
    private final OverrideRegistry overrideRegistry;
    private final List<RuleSet> ruleSets;
    private final StatusApiConfig statusApi;

    public Config(
            Path root,
            OverrideRegistry overrideRegistry,
            List<RuleSet> ruleSets
    ) {
        this(
                root,
                overrideRegistry,
                ruleSets,
                StatusApiConfig.disabled()
        );
    }

    public Config(
            Path root,
            OverrideRegistry overrideRegistry,
            List<RuleSet> ruleSets,
            StatusApiConfig statusApi
    ) {
        this.root = Objects.requireNonNull(root);
        this.overrideRegistry = Objects.requireNonNull(overrideRegistry);
        this.ruleSets = List.copyOf(ruleSets);
        this.statusApi = Objects.requireNonNull(statusApi);
    }

    public Path getRoot() {
        return root;
    }

    public OverrideRegistry getOverrides() {
        return overrideRegistry;
    }

    public List<RuleSet> getRuleSets() {
        return ruleSets;
    }

    public StatusApiConfig getStatusApi() {
        return statusApi;
    }
}
