package com.haydeproductions.project.config;

import com.haydeproductions.project.log.LogHandler;
import com.haydeproductions.project.quarantine.QuarantineService;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ConfigActionServices {

    private final LogHandler logHandler;
    private final QuarantineService quarantineService;
    private final Set<String> mirrorIds;

    private ConfigActionServices(
            LogHandler logHandler,
            QuarantineService quarantineService,
            Set<String> mirrorIds
    ) {
        this.logHandler = logHandler;
        this.quarantineService = quarantineService;
        this.mirrorIds = Set.copyOf(Objects.requireNonNull(mirrorIds));
    }

    public static ConfigActionServices none() {
        return new ConfigActionServices(null, null, Set.of());
    }

    public static ConfigActionServices withLogHandler(
            LogHandler logHandler
    ) {
        return new ConfigActionServices(
                Objects.requireNonNull(logHandler),
                null,
                Set.of()
        );
    }

    public static ConfigActionServices of(
            LogHandler logHandler,
            QuarantineService quarantineService
    ) {
        return new ConfigActionServices(
                Objects.requireNonNull(logHandler),
                Objects.requireNonNull(quarantineService),
                Set.of()
        );
    }

    public ConfigActionServices withMirrorIds(Set<String> mirrorIds) {
        return new ConfigActionServices(
                logHandler,
                quarantineService,
                mirrorIds
        );
    }

    public Optional<LogHandler> getLogHandler() {
        return Optional.ofNullable(logHandler);
    }

    public Optional<QuarantineService> getQuarantineService() {
        return Optional.ofNullable(quarantineService);
    }

    public Set<String> getMirrorIds() {
        return mirrorIds;
    }
}
