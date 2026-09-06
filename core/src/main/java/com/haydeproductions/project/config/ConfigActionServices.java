package com.haydeproductions.project.config;

import com.haydeproductions.project.log.LogHandler;
import com.haydeproductions.project.quarantine.QuarantineService;

import java.util.Objects;
import java.util.Optional;

public final class ConfigActionServices {

    private final LogHandler logHandler;
    private final QuarantineService quarantineService;

    private ConfigActionServices(
            LogHandler logHandler,
            QuarantineService quarantineService
    ) {
        this.logHandler = logHandler;
        this.quarantineService = quarantineService;
    }

    public static ConfigActionServices none() {
        return new ConfigActionServices(null, null);
    }

    public static ConfigActionServices withLogHandler(
            LogHandler logHandler
    ) {
        return new ConfigActionServices(
                Objects.requireNonNull(logHandler),
                null
        );
    }

    public static ConfigActionServices of(
            LogHandler logHandler,
            QuarantineService quarantineService
    ) {
        return new ConfigActionServices(
                Objects.requireNonNull(logHandler),
                Objects.requireNonNull(quarantineService)
        );
    }

    public Optional<LogHandler> getLogHandler() {
        return Optional.ofNullable(logHandler);
    }

    public Optional<QuarantineService> getQuarantineService() {
        return Optional.ofNullable(quarantineService);
    }
}
