package com.haydeproductions.project.config;

public record StatusApiConfig(
        boolean enabled,
        String host,
        int port
) {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;

    public StatusApiConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                    "statusApi.host cannot be blank"
            );
        }

        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException(
                    "statusApi.port must be between 0 and 65535"
            );
        }
    }

    public static StatusApiConfig disabled() {
        return new StatusApiConfig(
                false,
                DEFAULT_HOST,
                DEFAULT_PORT
        );
    }
}
