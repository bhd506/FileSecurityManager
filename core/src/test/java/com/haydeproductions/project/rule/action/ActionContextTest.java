package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.rule.FileContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionContextTest {

    @Test
    void returnsSuppliedFileContext() {
        FileContext file =
                new FileContext(Path.of("file.txt"));

        ActionContext context =
                new ActionContext(file);

        assertSame(
                file,
                context.getFile()
        );
    }

    @Test
    void rejectsNullFileContext() {
        assertThrows(
                NullPointerException.class,
                () -> new ActionContext(null)
        );
    }
}
