package com.haydeproductions.mirror.exception;

import java.io.IOException;

public final class InvalidMirrorTargetException extends IOException {
    public InvalidMirrorTargetException(String message) {
        super(message);
    }
}
