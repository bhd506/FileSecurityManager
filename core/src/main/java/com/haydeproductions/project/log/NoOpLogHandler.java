package com.haydeproductions.project.log;

public final class NoOpLogHandler implements LogHandler {

    public static final NoOpLogHandler INSTANCE = new NoOpLogHandler();

    private NoOpLogHandler() {
    }

    @Override
    public void log(LogEntry entry) {
        // Intentionally does nothing.
    }

    @Override
    public void logDeletion(DeletionRecord record) {
        // Intentionally does nothing.
    }
}
