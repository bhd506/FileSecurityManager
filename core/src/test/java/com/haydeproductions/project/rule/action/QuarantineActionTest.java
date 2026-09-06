package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.log.LogEventType;
import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.quarantine.QuarantineService;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import com.haydeproductions.project.testing.RecordingLogHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QuarantineActionTest {

    @TempDir
    Path tempDir;

    @Test
    void phaseIsQuarantine() throws IOException {
        QuarantineAction action = new QuarantineAction(
                new QuarantineService(tempDir.resolve("quarantine")),
                new RecordingLogHandler()
        );

        assertEquals(ActionPhase.QUARANTINE, action.getPhase());
    }

    @Test
    void movesSourceUpdatesCurrentPathAndSetsQuarantinedState() throws Exception {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "data");

        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(source, FileState.SCANNING);

        ActionContext context = new ActionContext(
                new FileContext(source),
                registry
        );

        RecordingLogHandler logs = new RecordingLogHandler();

        new QuarantineAction(
                new QuarantineService(tempDir.resolve("quarantine")),
                logs
        ).execute(context);

        assertFalse(Files.exists(source));
        assertTrue(Files.exists(context.getCurrentPath()));
        assertFalse(context.isAtOriginalPath());
        assertEquals(
                Optional.of(FileState.QUARANTINED),
                registry.getState(source)
        );
        assertEquals(1, logs.entries().size());
        assertEquals(
                LogEventType.QUARANTINE_SUCCESS,
                logs.entries().getFirst().type()
        );
    }

    @Test
    void duplicateQuarantineUsesOriginalSourceAndSecondExecutionSkips() throws Exception {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "data");

        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(source, FileState.SCANNING);

        ActionContext context = new ActionContext(
                new FileContext(source),
                registry
        );

        RecordingLogHandler logs = new RecordingLogHandler();
        QuarantineAction action = new QuarantineAction(
                new QuarantineService(tempDir.resolve("quarantine")),
                logs
        );

        action.execute(context);
        Path firstQuarantinePath = context.getCurrentPath();
        action.execute(context);

        assertEquals(firstQuarantinePath, context.getCurrentPath());
        assertTrue(Files.exists(firstQuarantinePath));
        assertEquals(2, logs.entries().size());
        assertEquals(
                LogEventType.QUARANTINE_SUCCESS,
                logs.entries().get(0).type()
        );
        assertEquals(
                LogEventType.QUARANTINE_SKIPPED_MISSING,
                logs.entries().get(1).type()
        );
    }

    @Test
    void missingSourceSkipsAndLogsWithoutChangingCurrentPath() throws Exception {
        Path source = tempDir.resolve("missing.txt");
        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(source, FileState.SCANNING);

        ActionContext context = new ActionContext(
                new FileContext(source),
                registry
        );

        RecordingLogHandler logs = new RecordingLogHandler();

        new QuarantineAction(
                new QuarantineService(tempDir.resolve("quarantine")),
                logs
        ).execute(context);

        assertTrue(context.isAtOriginalPath());
        assertEquals(
                Optional.of(FileState.SCANNING),
                registry.getState(source)
        );
        assertEquals(
                LogEventType.QUARANTINE_SKIPPED_MISSING,
                logs.entries().getFirst().type()
        );
    }

    @Test
    void rejectsNullDependencies() throws IOException {
        QuarantineService service = new QuarantineService(
                tempDir.resolve("quarantine")
        );

        assertThrows(
                NullPointerException.class,
                () -> new QuarantineAction(null, new RecordingLogHandler())
        );
        assertThrows(
                NullPointerException.class,
                () -> new QuarantineAction(service, null)
        );
    }
}
