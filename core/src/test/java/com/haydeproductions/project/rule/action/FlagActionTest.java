package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FlagActionTest {

    @TempDir
    Path tempDir;

    @Test
    void phaseIsFlag() {
        FlagAction action = new FlagAction();

        assertEquals(
                ActionPhase.FLAG,
                action.getPhase()
        );
    }

    @Test
    void executionSetsFileStateToFlagged() throws Exception {
        Path filePath = tempDir.resolve("file.txt");
        FileContext file = new FileContext(filePath);
        FileStateRegistry registry = new FileStateRegistry();

        FlagAction action = new FlagAction();

        action.execute(
                new ActionContext(file, registry)
        );

        assertEquals(
                Optional.of(FileState.FLAGGED),
                registry.getState(filePath)
        );
    }

    @Test
    void executionReplacesScanningStateWithFlagged() throws Exception {
        Path filePath = tempDir.resolve("file.txt");
        FileContext file = new FileContext(filePath);
        FileStateRegistry registry = new FileStateRegistry();

        registry.setState(
                filePath,
                FileState.SCANNING
        );

        new FlagAction().execute(
                new ActionContext(file, registry)
        );

        assertEquals(
                Optional.of(FileState.FLAGGED),
                registry.getState(filePath)
        );
    }

    @Test
    void repeatedExecutionRemainsFlagged() throws Exception {
        Path filePath = tempDir.resolve("file.txt");
        FileContext file = new FileContext(filePath);
        FileStateRegistry registry = new FileStateRegistry();
        ActionContext context = new ActionContext(file, registry);

        FlagAction action = new FlagAction();

        action.execute(context);
        action.execute(context);

        assertEquals(
                Optional.of(FileState.FLAGGED),
                registry.getState(filePath)
        );
    }
}
