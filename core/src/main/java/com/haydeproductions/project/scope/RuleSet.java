package com.haydeproductions.project.scope;

import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.scan.ScanSession;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class RuleSet {
    private final Path root;
    private final int maxDepth;
    private final OverrideRegistry overrideRegistry;
    private final List<Rule> rules;

    public RuleSet(Builder builder) {
        this.root = Objects.requireNonNull(builder.root)
                .toAbsolutePath()
                .normalize();

        this.maxDepth = builder.maxDepth;
        this.overrideRegistry =
                Objects.requireNonNull(builder.overrideRegistry);

        this.rules = List.copyOf(builder.rules);
    }

    public List<Rule> getRules() {
        return rules;
    }

    public Path getRoot() {
        return root;
    }

    public void applyRule(Path file){
        // Legacy traversal hook retained for compatibility with full-tree tests.
    }

    public void applyRules(ScanSession session)
            throws ConditionEvaluationException {

        Path file = session.getFile().getPath();

        if (!isFileOwned(file)) {
            return;
        }

        for (Rule rule : rules) {
            session.evaluate(rule);
        }
    }

    public void applyRules() throws IOException {
        Files.walkFileTree(
                root,
                EnumSet.noneOf(FileVisitOption.class),
                maxDepth,
                new SimpleFileVisitor<>() {

                    @Override
                                        public FileVisitResult preVisitDirectory(
                            Path dir,
                            BasicFileAttributes attrs) {

                        if (!dir.equals(root) && overrideRegistry.isOverride(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                                        public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attrs) {

                        if (attrs.isRegularFile()) {
                            applyRule(file);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                }
        );
    }

    public boolean isFileOwned(Path file) {
        Path normalizedFile = file.toAbsolutePath().normalize();

        int depth = normalizedFile.getNameCount() - root.getNameCount();

        // Cheapest rejection first.
        if (depth < 0 || depth > maxDepth) {
            return false;
        }

        // Makes the method correct even when called without RuleSetIndex.
        if (!normalizedFile.startsWith(root)) {
            return false;
        }

        // An override below this RuleSet's root blocks inheritance.
        Path current = normalizedFile.getParent();

        while (current != null && !current.equals(root)) {
            if (overrideRegistry.isOverride(current)) {
                return false;
            }

            current = current.getParent();
        }

        return true;
    }

    public static Builder builder(Path root, OverrideRegistry overrideRegistry){
        return new Builder(root, overrideRegistry);
    }

    public static class Builder {
        private final Path root;
        private final OverrideRegistry overrideRegistry;
        private final List<Rule> rules = new ArrayList<>();
        private int maxDepth = Integer.MAX_VALUE;

        private Builder(Path root, OverrideRegistry overrideRegistry){
            this.root = root;
            this.overrideRegistry = overrideRegistry;
        }

        public Builder maxDepth(int maxDepth) {
            if (maxDepth < 0) {
                throw new IllegalArgumentException(
                        "maxDepth cannot be negative"
                );
            }

            this.maxDepth = maxDepth;
            return this;
        }

        public Builder rule(Rule rule) {
            this.rules.add(Objects.requireNonNull(rule));
            return this;
        }

        public Builder rules(Collection<Rule> rules) {
            this.rules.addAll(rules);
            return this;
        }

        public RuleSet build(){
            return new RuleSet(this);
        }
    }
}