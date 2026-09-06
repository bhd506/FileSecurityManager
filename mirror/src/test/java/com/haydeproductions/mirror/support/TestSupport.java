package com.haydeproductions.mirror.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

public final class TestSupport {
    private TestSupport() {
    }

    public static Path write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    public static void await(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Condition was not met within " + timeout);
    }

    public static void awaitContent(Path file, String content) throws InterruptedException {
        await(Duration.ofSeconds(5), () -> {
            try {
                return Files.exists(file) && Files.readString(file).equals(content);
            } catch (IOException e) {
                return false;
            }
        });
    }

    public static void awaitMissing(Path file) throws InterruptedException {
        await(Duration.ofSeconds(5), () -> !Files.exists(file));
    }
}
