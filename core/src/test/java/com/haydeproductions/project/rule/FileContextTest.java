package com.haydeproductions.project.rule;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileContextTest {

    @Test
    void returnsSuppliedPath() {
        Path path = Path.of("file.txt");

        FileContext context =
                new FileContext(path);

        assertSame(
                path,
                context.getPath()
        );
    }

    @Test
    void rejectsNullPath() {
        assertThrows(
                NullPointerException.class,
                () -> new FileContext(null)
        );
    }
}
