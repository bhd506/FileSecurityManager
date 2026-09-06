package com.haydeproductions.mirror.state;

import com.haydeproductions.mirror.model.FileFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SyncStateStoreTest {
    @TempDir Path temp;

    @Test
    void inMemoryStoreRoundTripsAndRemoves() throws Exception {
        SyncStateStore store = new InMemorySyncStateStore();
        Path key = Path.of("a/file.txt");
        FileFingerprint value = FileFingerprint.present(10, "abc");
        assertTrue(store.get(key).isEmpty());
        store.put(key, value);
        assertEquals(value, store.get(key).orElseThrow());
        store.remove(key);
        assertTrue(store.get(key).isEmpty());
    }

    @Test
    void propertiesStorePersistsPresentAndAbsentStates() throws Exception {
        Path file = temp.resolve("state/store.properties");
        Path presentKey = Path.of("a.txt");
        Path absentKey = Path.of("nested/b.txt");
        FileFingerprint present = FileFingerprint.present(7, "deadbeef");

        PropertiesSyncStateStore first = new PropertiesSyncStateStore(file);
        first.put(presentKey, present);
        first.put(absentKey, FileFingerprint.absent());

        PropertiesSyncStateStore second = new PropertiesSyncStateStore(file);
        assertEquals(present, second.get(presentKey).orElseThrow());
        assertEquals(FileFingerprint.absent(), second.get(absentKey).orElseThrow());
        second.remove(presentKey);

        PropertiesSyncStateStore third = new PropertiesSyncStateStore(file);
        assertTrue(third.get(presentKey).isEmpty());
        assertEquals(FileFingerprint.absent(), third.get(absentKey).orElseThrow());
    }
}
