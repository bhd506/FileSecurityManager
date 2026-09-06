package com.haydeproductions.mirror.api;

@FunctionalInterface
public interface MirrorErrorHandler {
    void onError(Throwable error);

    static MirrorErrorHandler stderr() {
        return Throwable::printStackTrace;
    }
}
