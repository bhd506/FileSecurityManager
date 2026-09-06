package com.haydeproductions.project.config;

public final class ConfigException extends IllegalArgumentException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
