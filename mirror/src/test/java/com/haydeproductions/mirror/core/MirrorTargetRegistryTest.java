package com.haydeproductions.mirror.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MirrorTargetRegistryTest {
    @Test
    void addRemoveContainsAndSnapshotWork() {
        MirrorTargetRegistry registry = new MirrorTargetRegistry();
        Path a = Path.of("a.txt");
        assertTrue(registry.add(a));
        assertFalse(registry.add(a));
        assertTrue(registry.contains(a));
        assertEquals(Set.of(a), registry.snapshot());
        assertTrue(registry.remove(a));
        assertFalse(registry.remove(a));
        assertFalse(registry.contains(a));
    }

    @Test
    void affectedByMatchesExactTargetAndDescendants() {
        MirrorTargetRegistry registry = new MirrorTargetRegistry();
        Path a = Path.of("folder/a.txt");
        Path b = Path.of("folder/deeper/b.txt");
        Path c = Path.of("other/c.txt");
        registry.add(a);
        registry.add(b);
        registry.add(c);
        assertEquals(Set.of(a, b), registry.affectedBy(Path.of("folder")));
        assertEquals(Set.of(a), registry.affectedBy(a));
        assertTrue(registry.affectedBy(Path.of("missing")).isEmpty());
    }

    @Test
    void snapshotIsIndependentAndUnmodifiable() {
        MirrorTargetRegistry registry = new MirrorTargetRegistry();
        registry.add(Path.of("a"));
        Set<Path> snapshot = registry.snapshot();
        registry.add(Path.of("b"));
        assertEquals(Set.of(Path.of("a")), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(Path.of("c")));
    }
}
