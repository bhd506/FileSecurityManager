package com.haydeproductions.project.status;

import com.haydeproductions.project.state.FileState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileStatusTest {

    @Test
    void trackedFactoryCreatesTrackedStatus() {
        FileStatus status = FileStatus.tracked("file.txt", FileState.SAFE);

        assertTrue(status.isTracked());
        assertEquals(Optional.of(FileState.SAFE), status.state());
        assertEquals("SAFE", status.getStateName());
        assertTrue(status.isSafe());
    }

    @Test
    void untrackedFactoryCreatesUntrackedStatus() {
        FileStatus status = FileStatus.untracked("file.txt");

        assertFalse(status.isTracked());
        assertEquals("UNTRACKED", status.getStateName());
        assertFalse(status.isSafe());
    }


    @Test
    void onlySafeStateReportsSafe() {
        for (FileState state : FileState.values()) {
            FileStatus status = FileStatus.tracked("file.txt", state);

            assertEquals(
                    state == FileState.SAFE,
                    status.isSafe()
            );
        }
    }

    @Test
    void rejectsBlankPathAndNullStateOptional() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileStatus(" ", Optional.of(FileState.SAFE))
        );

        assertThrows(
                NullPointerException.class,
                () -> new FileStatus("file.txt", null)
        );
    }
}
