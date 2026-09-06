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
    private final RuntimeConfig runtime;
    private final List<MirrorDefinition> mirrors;

    public Config(
            Path root,
            OverrideRegistry overrideRegistry,
            List<RuleSet> ruleSets
    ) {
        this(
                root,
                overrideRegistry,
                ruleSets,
                StatusApiConfig.disabled(),
                RuntimeConfig.defaults(),
                List.of()
        );
    }

    public Config(
            Path root,
            OverrideRegistry overrideRegistry,
            List<RuleSet> ruleSets,
            StatusApiConfig statusApi
    ) {
        this(
                root,
                overrideRegistry,
                ruleSets,
                statusApi,
                RuntimeConfig.defaults(),
                List.of()
        );
    }

    public Config(
            Path root,
            OverrideRegistry overrideRegistry,
            List<RuleSet> ruleSets,
            StatusApiConfig statusApi,
            RuntimeConfig runtime,
            List<MirrorDefinition> mirrors
    ) {
        this.root = Objects.requireNonNull(root)
                .toAbsolutePath()
                .normalize();
        this.overrideRegistry = Objects.requireNonNull(overrideRegistry);
        this.ruleSets = List.copyOf(Objects.requireNonNull(ruleSets));
        this.statusApi = Objects.requireNonNull(statusApi);
        this.runtime = Objects.requireNonNull(runtime);
        this.mirrors = List.copyOf(Objects.requireNonNull(mirrors));
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

    public RuntimeConfig getRuntime() {
        return runtime;
    }

    public List<MirrorDefinition> getMirrors() {
        return mirrors;
    }
}
