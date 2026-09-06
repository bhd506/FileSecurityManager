package com.haydeproductions.project.config;

import java.time.Duration;
import java.util.Objects;

public record RuntimeConfig(
        Duration debounce,
        int workerThreads
) {
    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(150);

    public RuntimeConfig {
        Objects.requireNonNull(debounce, "debounce");
        if (debounce.isNegative()) {
            throw new IllegalArgumentException("Runtime debounce cannot be negative");
        }
        if (workerThreads < 1) {
            throw new IllegalArgumentException("Runtime workerThreads must be positive");
        }
    }

    public static RuntimeConfig defaults() {
        return new RuntimeConfig(
                DEFAULT_DEBOUNCE,
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
    }
}
