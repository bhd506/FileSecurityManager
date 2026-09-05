package com.haydeproductions.project.config;

import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;

import java.nio.file.Path;
import java.util.List;

public final class Config {

    private final Path root;
    private final OverrideRegistry overrideRegistry;
    private final List<RuleSet> ruleSets;

    public Config(
            Path root,
            OverrideRegistry overrideRegistry,
            List<RuleSet> ruleSets
    ) {
        this.root = root;
        this.overrideRegistry = overrideRegistry;
        this.ruleSets = List.copyOf(ruleSets);
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
}