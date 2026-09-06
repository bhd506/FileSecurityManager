package com.haydeproductions.mirror.state;

import com.haydeproductions.mirror.model.FileFingerprint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public final class PropertiesSyncStateStore implements SyncStateStore {
    private static final String ABSENT = "A";
    private final Path file;
    private final Properties properties = new Properties();

    public PropertiesSyncStateStore(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        load();
    }

    @Override
    public synchronized Optional<FileFingerprint> get(Path relative) {
        String value = properties.getProperty(key(relative));
        return value == null ? Optional.empty() : Optional.of(decode(value));
    }

    @Override
    public synchronized void put(Path relative, FileFingerprint fingerprint) throws IOException {
        properties.setProperty(key(relative), encode(fingerprint));
        persist();
    }

    @Override
    public synchronized void remove(Path relative) throws IOException {
        properties.remove(key(relative));
        persist();
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
    }

    private void persist() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, "mirror sync state");
        }
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String key(Path relative) {
        return relative.normalize().toString();
    }

    private static String encode(FileFingerprint fingerprint) {
        if (!fingerprint.exists()) {
            return ABSENT;
        }
        return "F:" + fingerprint.size() + ":" + fingerprint.sha256();
    }

    private static FileFingerprint decode(String value) {
        if (ABSENT.equals(value)) {
            return FileFingerprint.absent();
        }
        if (!value.startsWith("F:")) {
            throw new IllegalStateException("Invalid sync state entry: " + value);
        }
        String[] parts = value.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalStateException("Invalid sync state entry: " + value);
        }
        return FileFingerprint.present(Long.parseLong(parts[1]), parts[2]);
    }
}
