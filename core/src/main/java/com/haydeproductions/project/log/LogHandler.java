package com.haydeproductions.project.log;

import java.io.IOException;

public interface LogHandler {

    void log(LogEntry entry) throws IOException;

    void logDeletion(DeletionRecord record) throws IOException;
}
