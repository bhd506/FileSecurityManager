package com.haydeproductions.mirror.core;

import com.haydeproductions.mirror.exception.InvalidMirrorTargetException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MirrorPathMapperTest {
    @TempDir Path temp;

    @Test
    void mapsPathsBothDirections() throws Exception {
        Path source = temp.resolve("source");
        Path mirror = temp.resolve("mirror");
        MirrorPathMapper mapper = new MirrorPathMapper(source, mirror);
        Path relative = Path.of("a/b/file.txt");
        assertEquals(source.resolve(relative).toAbsolutePath().normalize(), mapper.source(relative));
        assertEquals(mirror.resolve(relative).toAbsolutePath().normalize(), mapper.mirror(relative));
        assertEquals(relative, mapper.toRelativeSource(source.resolve(relative)));
        assertEquals(relative, mapper.relativeFromMirror(mirror.resolve(relative)));
    }

    @Test
    void rejectsPathsOutsideManagedRoots() {
        MirrorPathMapper mapper = new MirrorPathMapper(temp.resolve("source"), temp.resolve("mirror"));
        assertThrows(InvalidMirrorTargetException.class, () -> mapper.toRelativeSource(temp.resolve("outside.txt")));
        assertThrows(InvalidMirrorTargetException.class, () -> mapper.relativeFromMirror(temp.resolve("outside.txt")));
        assertThrows(InvalidMirrorTargetException.class, () -> mapper.source(Path.of("../escape.txt")));
        assertThrows(InvalidMirrorTargetException.class, () -> mapper.mirror(Path.of("../escape.txt")));
    }

    @Test
    void rejectsRootItselfAsTarget() {
        Path source = temp.resolve("source");
        MirrorPathMapper mapper = new MirrorPathMapper(source, temp.resolve("mirror"));
        assertThrows(InvalidMirrorTargetException.class, () -> mapper.toRelativeSource(source));
    }
}
