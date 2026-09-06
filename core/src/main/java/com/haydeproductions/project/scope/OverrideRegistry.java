package com.haydeproductions.project.scope;

import java.nio.file.Path;
import java.util.Set;

public class OverrideRegistry {
    private final Set<Path> overriddenPaths;

    public OverrideRegistry(Set<Path> overriddenPaths){
        this.overriddenPaths = Set.copyOf(overriddenPaths);
    }

    public boolean isOverride(Path path){
        return this.overriddenPaths.contains(path);
    }

    public void logOverrides(){
        for (Path path : this.overriddenPaths){
            System.out.println(path);
        }
    }
}
