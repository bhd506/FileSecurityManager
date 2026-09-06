package com.haydeproductions.project.testing;

import com.haydeproductions.project.log.DeletionRecord;
import com.haydeproductions.project.log.LogEntry;
import com.haydeproductions.project.log.LogHandler;

import java.util.ArrayList;
import java.util.List;

public final class RecordingLogHandler implements LogHandler {

    private final List<LogEntry> entries = new ArrayList<>();
    private final List<DeletionRecord> deletions = new ArrayList<>();

    @Override
    public synchronized void log(LogEntry entry) {
        entries.add(entry);
    }

    @Override
    public synchronized void logDeletion(DeletionRecord record) {
        deletions.add(record);
    }

    public synchronized List<LogEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized List<DeletionRecord> deletions() {
        return List.copyOf(deletions);
    }
}
