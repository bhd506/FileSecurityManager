package com.haydeproductions.project.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class FileLogHandler implements LogHandler {

    private final Path eventLogPath;
    private final Path deletionLogPath;
    private final Object eventLock = new Object();
    private final Object deletionLock = new Object();

    public FileLogHandler(
            Path eventLogPath,
            Path deletionLogPath
    ) throws IOException {
        this.eventLogPath = normalize(eventLogPath);
        this.deletionLogPath = normalize(deletionLogPath);

        createParent(this.eventLogPath);
        createParent(this.deletionLogPath);
    }

    public Path getEventLogPath() {
        return eventLogPath;
    }

    public Path getDeletionLogPath() {
        return deletionLogPath;
    }

    @Override
    public void log(LogEntry entry) throws IOException {
        Objects.requireNonNull(entry);

        String json = "{" +
                field("eventId", entry.eventId()) + "," +
                field("timestamp", entry.timestamp().toString()) + "," +
                field("type", entry.type().name()) + "," +
                field("originalPath", entry.originalPath().toString()) + "," +
                field("currentPath", entry.currentPath().toString()) + "," +
                field("message", entry.message()) +
                "}\n";

        synchronized (eventLock) {
            appendDurably(eventLogPath, json);
        }
    }

    @Override
    public void logDeletion(DeletionRecord record) throws IOException {
        Objects.requireNonNull(record);

        String json = "{" +
                field("deletionId", record.deletionId()) + "," +
                field("deletedAt", record.deletedAt().toString()) + "," +
                field("originalPath", record.originalPath().toString()) + "," +
                field("deletedPath", record.deletedPath().toString()) + "," +
                numberField("size", record.size()) + "," +
                field("sha256", record.sha256()) +
                "}\n";

        synchronized (deletionLock) {
            appendDurably(deletionLogPath, json);
        }
    }

    private void appendDurably(Path path, String value)
            throws IOException {

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
        )) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }

            channel.force(true);
        }
    }

    private String field(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value) + "\"";
    }

    private String numberField(String name, long value) {
        return "\"" + escape(name) + "\":" + value;
    }

    private String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }

        return builder.toString();
    }

    private Path normalize(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }

    private void createParent(Path path) throws IOException {
        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
