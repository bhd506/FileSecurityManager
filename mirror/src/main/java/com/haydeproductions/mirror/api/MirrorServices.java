package com.haydeproductions.mirror.api;

import com.haydeproductions.mirror.config.MirrorConfig;

import java.io.IOException;

public final class MirrorServices {
    private MirrorServices() {
    }

    public static MirrorService create(MirrorConfig config) throws IOException {
        return create(config, MirrorErrorHandler.stderr());
    }

    public static MirrorService create(
            MirrorConfig config,
            MirrorErrorHandler errorHandler
    ) throws IOException {
        return new DefaultMirrorService(config, errorHandler);
    }
}
