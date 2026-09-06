package com.haydeproductions.project.status;

public final class InvalidStatusPathException extends IllegalArgumentException {

    public InvalidStatusPathException(String message) {
        super(message);
    }

    public InvalidStatusPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
