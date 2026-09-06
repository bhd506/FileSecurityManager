package com.haydeproductions.project.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FileLogHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsParentDirectories() throws IOException {
        Path eventLog = tempDir.resolve("logs/events/events.jsonl");
        Path deletionLog = tempDir.resolve("logs/deletions/deletions.jsonl");

        new FileLogHandler(eventLog, deletionLog);

        assertTrue(Files.isDirectory(eventLog.getParent()));
        assertTrue(Files.isDirectory(deletionLog.getParent()));
    }

    @Test
    void appendsEventAsJsonLine() throws IOException {
        Path eventLog = tempDir.resolve("events.jsonl");
        Path deletionLog = tempDir.resolve("deletions.jsonl");

        FileLogHandler handler = new FileLogHandler(eventLog, deletionLog);

        LogEntry entry = new LogEntry(
                "event-1",
                Instant.parse("2026-09-06T00:00:00Z"),
                LogEventType.QUARANTINE_SUCCESS,
                tempDir.resolve("source.txt"),
                tempDir.resolve("quarantine/source.txt"),
                "quoted \"message\"\nnext"
        );

        handler.log(entry);

        String line = Files.readString(eventLog);

        assertTrue(line.endsWith("\n"));
        assertTrue(line.contains("\"eventId\":\"event-1\""));
        assertTrue(line.contains("\"type\":\"QUARANTINE_SUCCESS\""));
        assertTrue(line.contains("quoted \\\"message\\\"\\nnext"));
    }

    @Test
    void appendsMultipleEventsWithoutOverwriting() throws IOException {
        Path eventLog = tempDir.resolve("events.jsonl");
        Path deletionLog = tempDir.resolve("deletions.jsonl");

        FileLogHandler handler = new FileLogHandler(eventLog, deletionLog);

        handler.log(LogEntry.create(
                LogEventType.DELETE_SKIPPED_MISSING,
                tempDir.resolve("a.txt"),
                tempDir.resolve("a.txt"),
                "first"
        ));

        handler.log(LogEntry.create(
                LogEventType.DELETE_SKIPPED_MISSING,
                tempDir.resolve("b.txt"),
                tempDir.resolve("b.txt"),
                "second"
        ));

        assertEquals(2, Files.readAllLines(eventLog).size());
    }

    @Test
    void writesDeletionRecordToSeparateDeletionLog() throws IOException {
        Path eventLog = tempDir.resolve("events.jsonl");
        Path deletionLog = tempDir.resolve("deletions.jsonl");

        FileLogHandler handler = new FileLogHandler(eventLog, deletionLog);

        DeletionRecord record = new DeletionRecord(
                "delete-1",
                Instant.parse("2026-09-06T00:00:00Z"),
                tempDir.resolve("source.txt"),
                tempDir.resolve("quarantine/source.txt"),
                123,
                "abcdef"
        );

        handler.logDeletion(record);

        String line = Files.readString(deletionLog);

        assertTrue(line.contains("\"deletionId\":\"delete-1\""));
        assertTrue(line.contains("\"size\":123"));
        assertTrue(line.contains("\"sha256\":\"abcdef\""));
        assertFalse(Files.exists(eventLog));
    }

    @Test
    void exposesNormalizedLogPaths() throws IOException {
        Path eventLog = tempDir.resolve("a/../events.jsonl");
        Path deletionLog = tempDir.resolve("b/../deletions.jsonl");

        FileLogHandler handler = new FileLogHandler(eventLog, deletionLog);

        assertEquals(
                eventLog.toAbsolutePath().normalize(),
                handler.getEventLogPath()
        );
        assertEquals(
                deletionLog.toAbsolutePath().normalize(),
                handler.getDeletionLogPath()
        );
    }

    @Test
    void rejectsNullPaths() {
        assertThrows(
                NullPointerException.class,
                () -> new FileLogHandler(null, tempDir.resolve("d.jsonl"))
        );

        assertThrows(
                NullPointerException.class,
                () -> new FileLogHandler(tempDir.resolve("e.jsonl"), null)
        );
    }
}
