package com.haydeproductions.project.scope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OverrideRegistryTest {

    @TempDir
    Path root;

    @Test
    void registeredPathIsOverride() {
        Path path = root.resolve("private");
        OverrideRegistry registry = new OverrideRegistry(Set.of(path));

        assertTrue(registry.isOverride(path));
    }

    @Test
    void unregisteredPathIsNotOverride() {
        OverrideRegistry registry = new OverrideRegistry(
                Set.of(root.resolve("private"))
        );

        assertFalse(registry.isOverride(root.resolve("public")));
    }

    @Test
    void childOfOverrideIsNotAutomaticallyAnOverride() {
        Path override = root.resolve("private");
        OverrideRegistry registry = new OverrideRegistry(Set.of(override));

        assertFalse(registry.isOverride(override.resolve("nested")));
    }

    @Test
    void parentOfOverrideIsNotAutomaticallyAnOverride() {
        Path override = root.resolve("private/nested");
        OverrideRegistry registry = new OverrideRegistry(Set.of(override));

        assertFalse(registry.isOverride(root.resolve("private")));
    }

    @Test
    void emptyRegistryContainsNoOverrides() {
        OverrideRegistry registry = new OverrideRegistry(Set.of());

        assertFalse(registry.isOverride(root));
        assertFalse(registry.isOverride(root.resolve("anything")));
    }

    @Test
    void registryTakesDefensiveCopyOfInputSet() {
        Set<Path> source = new HashSet<>();
        Path original = root.resolve("original");
        Path addedLater = root.resolve("added-later");
        source.add(original);

        OverrideRegistry registry = new OverrideRegistry(source);
        source.add(addedLater);
        source.remove(original);

        assertTrue(registry.isOverride(original));
        assertFalse(registry.isOverride(addedLater));
    }

    @Test
    void logOverridesPrintsEveryRegisteredPath() {
        Path first = root.resolve("first");
        Path second = root.resolve("second");
        OverrideRegistry registry = new OverrideRegistry(Set.of(first, second));

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            registry.logOverrides();
        } finally {
            System.setOut(originalOut);
        }

        String printed = output.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains(first.toString()));
        assertTrue(printed.contains(second.toString()));
    }
}
