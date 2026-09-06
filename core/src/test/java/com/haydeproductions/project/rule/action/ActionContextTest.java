package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.state.FileStateRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ActionContextTest {

    @Test
    void returnsSuppliedFileContext() {
        FileContext file = new FileContext(Path.of("file.txt"));
        FileStateRegistry registry = new FileStateRegistry();

        ActionContext context = new ActionContext(file, registry);

        assertSame(file, context.getFile());
    }

    @Test
    void returnsSuppliedStateRegistry() {
        FileContext file = new FileContext(Path.of("file.txt"));
        FileStateRegistry registry = new FileStateRegistry();

        ActionContext context = new ActionContext(file, registry);

        assertSame(registry, context.getStateRegistry());
    }

    @Test
    void rejectsNullFileContext() {
        assertThrows(
                NullPointerException.class,
                () -> new ActionContext(
                        null,
                        new FileStateRegistry()
                )
        );
    }

    @Test
    void rejectsNullStateRegistry() {
        assertThrows(
                NullPointerException.class,
                () -> new ActionContext(
                        new FileContext(Path.of("file.txt")),
                        null
                )
        );
    }

    @Test
    void originalAndCurrentPathStartAtNormalizedFilePath() {
        FileContext file = new FileContext(
                Path.of("folder").resolve("..").resolve("file.txt")
        );
        FileStateRegistry registry = new FileStateRegistry();

        ActionContext context = new ActionContext(file, registry);

        Path expected = file.getPath()
                .toAbsolutePath()
                .normalize();

        assertEquals(expected, context.getOriginalPath());
        assertEquals(expected, context.getCurrentPath());
        assertTrue(context.isAtOriginalPath());
    }

    @Test
    void currentPathCanMoveWithoutChangingOriginalPath() {
        FileContext file = new FileContext(Path.of("source.txt"));
        FileStateRegistry registry = new FileStateRegistry();
        ActionContext context = new ActionContext(file, registry);

        Path original = context.getOriginalPath();
        Path moved = Path.of("quarantine").resolve("source.txt");

        context.setCurrentPath(moved);

        assertEquals(original, context.getOriginalPath());
        assertEquals(moved.toAbsolutePath().normalize(), context.getCurrentPath());
        assertFalse(context.isAtOriginalPath());
    }

    @Test
    void setCurrentPathRejectsNull() {
        ActionContext context = new ActionContext(
                new FileContext(Path.of("file.txt")),
                new FileStateRegistry()
        );

        assertThrows(
                NullPointerException.class,
                () -> context.setCurrentPath(null)
        );
    }
}
