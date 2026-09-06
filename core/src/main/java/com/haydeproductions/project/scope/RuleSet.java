package com.haydeproductions.project.scope;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class RuleSet {
    private final Path root;
    private final int maxDepth;
    private final OverrideRegistry overrideRegistry;

    public RuleSet(Builder builder){
        this.root = builder.root;
        this.maxDepth = builder.maxDepth;
        this.overrideRegistry = builder.overrideRegistry;
    }

    public void applyRule(Path file){
        System.out.println(file);
    }

    public void applyRules() throws IOException {
        Files.walkFileTree(
                root,
                EnumSet.noneOf(FileVisitOption.class),
                maxDepth,
                new SimpleFileVisitor<>() {

                    @Override
                    @NotNull
                    public FileVisitResult preVisitDirectory(
                            @NotNull Path dir,
                            @NotNull BasicFileAttributes attrs) {

                        if (!dir.equals(root) && overrideRegistry.isOverride(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    @NotNull
                    public FileVisitResult visitFile(
                            @NotNull Path file,
                            @NotNull BasicFileAttributes attrs) {

                        if (attrs.isRegularFile()) {
                            applyRule(file);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                }
        );
    }

    public static Builder builder(Path root, OverrideRegistry overrideRegistry){
        return new Builder(root, overrideRegistry);
    }

    public static class Builder {
        private final Path root;
        private final OverrideRegistry overrideRegistry;
        private int maxDepth = Integer.MAX_VALUE;

        private Builder(Path root, OverrideRegistry overrideRegistry){
            this.root = root;
            this.overrideRegistry = overrideRegistry;
        }

        public Builder maxDepth(int maxDepth){
            this.maxDepth = maxDepth;
            return this;
        }

        public RuleSet build(){
            return new RuleSet(this);
        }
    }
}