package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.log.DeletionRecord;
import com.haydeproductions.project.log.LogEventType;
import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import com.haydeproductions.project.testing.RecordingLogHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeleteActionTest {

    @TempDir
    Path tempDir;

    @Test
    void phaseIsDelete() {
        assertEquals(
                ActionPhase.DELETE,
                new DeleteAction(new RecordingLogHandler()).getPhase()
        );
    }

    @Test
    void deletesCurrentFileRemovesActiveStateAndRecordsDeletion() throws Exception {
        Path source = tempDir.resolve("file.txt");
        Files.writeString(source, "hello");

        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(source, FileState.SCANNING);

        ActionContext context = new ActionContext(
                new FileContext(source),
                registry
        );

        RecordingLogHandler logs = new RecordingLogHandler();

        new DeleteAction(logs).execute(context);

        assertFalse(Files.exists(source));
        assertEquals(Optional.empty(), registry.getState(source));
        assertEquals(1, logs.deletions().size());

        DeletionRecord record = logs.deletions().getFirst();
        assertEquals(source.toAbsolutePath().normalize(), record.originalPath());
        assertEquals(source.toAbsolutePath().normalize(), record.deletedPath());
        assertEquals(5, record.size());
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                record.sha256()
        );
        assertEquals(
                LogEventType.DELETE_SUCCESS,
                logs.entries().getFirst().type()
        );
    }

    @Test
    void deletesCurrentQuarantinePathButRemovesOriginalState() throws Exception {
        Path original = tempDir.resolve("source.txt");
        Path quarantined = tempDir.resolve("quarantine/source.txt");
        Files.createDirectories(quarantined.getParent());
        Files.writeString(quarantined, "data");

        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(original, FileState.QUARANTINED);

        ActionContext context = new ActionContext(
                new FileContext(original),
                registry
        );
        context.setCurrentPath(quarantined);

        RecordingLogHandler logs = new RecordingLogHandler();

        new DeleteAction(logs).execute(context);

        assertFalse(Files.exists(quarantined));
        assertEquals(Optional.empty(), registry.getState(original));
        assertEquals(
                quarantined.toAbsolutePath().normalize(),
                logs.deletions().getFirst().deletedPath()
        );
        assertEquals(
                original.toAbsolutePath().normalize(),
                logs.deletions().getFirst().originalPath()
        );
    }

    @Test
    void missingTargetSkipsLogsAndRemovesStaleActiveState() throws Exception {
        Path source = tempDir.resolve("missing.txt");

        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(source, FileState.SCANNING);

        ActionContext context = new ActionContext(
                new FileContext(source),
                registry
        );

        RecordingLogHandler logs = new RecordingLogHandler();

        new DeleteAction(logs).execute(context);

        assertEquals(Optional.empty(), registry.getState(source));
        assertTrue(logs.deletions().isEmpty());
        assertEquals(
                LogEventType.DELETE_SKIPPED_MISSING,
                logs.entries().getFirst().type()
        );
    }

    @Test
    void duplicateDeleteCreatesOnlyOneDeletionRecord() throws Exception {
        Path source = tempDir.resolve("file.txt");
        Files.writeString(source, "data");

        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(source, FileState.SCANNING);

        ActionContext context = new ActionContext(
                new FileContext(source),
                registry
        );

        RecordingLogHandler logs = new RecordingLogHandler();
        DeleteAction action = new DeleteAction(logs);

        action.execute(context);
        action.execute(context);

        assertEquals(1, logs.deletions().size());
        assertEquals(2, logs.entries().size());
        assertEquals(LogEventType.DELETE_SUCCESS, logs.entries().get(0).type());
        assertEquals(
                LogEventType.DELETE_SKIPPED_MISSING,
                logs.entries().get(1).type()
        );
    }

    @Test
    void laterFileAtSamePathGetsDistinctDeletionIdentityAndFingerprint() throws Exception {
        Path source = tempDir.resolve("file.txt");
        RecordingLogHandler logs = new RecordingLogHandler();
        FileStateRegistry registry = new FileStateRegistry();

        Files.writeString(source, "first");
        registry.setState(source, FileState.SCANNING);
        new DeleteAction(logs).execute(
                new ActionContext(new FileContext(source), registry)
        );

        Files.writeString(source, "second");
        registry.setState(source, FileState.SCANNING);
        new DeleteAction(logs).execute(
                new ActionContext(new FileContext(source), registry)
        );

        assertEquals(2, logs.deletions().size());
        assertNotEquals(
                logs.deletions().get(0).deletionId(),
                logs.deletions().get(1).deletionId()
        );
        assertNotEquals(
                logs.deletions().get(0).sha256(),
                logs.deletions().get(1).sha256()
        );
    }

    @Test
    void rejectsNullLogHandler() {
        assertThrows(
                NullPointerException.class,
                () -> new DeleteAction(null)
        );
    }
}
