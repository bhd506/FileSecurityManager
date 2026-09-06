package com.haydeproductions.mirror.core;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MirrorTargetRegistry {
    private final Set<Path> targets = ConcurrentHashMap.newKeySet();

    public boolean add(Path relative) {
        return targets.add(relative.normalize());
    }

    public boolean remove(Path relative) {
        return targets.remove(relative.normalize());
    }

    public boolean contains(Path relative) {
        return targets.contains(relative.normalize());
    }

    public Set<Path> snapshot() {
        return Collections.unmodifiableSet(new HashSet<>(targets));
    }

    public Set<Path> affectedBy(Path relativeEventPath) {
        Path normalised = relativeEventPath.normalize();
        Set<Path> affected = new HashSet<>();
        for (Path target : targets) {
            if (target.equals(normalised) || target.startsWith(normalised)) {
                affected.add(target);
            }
        }
        return affected;
    }
}
