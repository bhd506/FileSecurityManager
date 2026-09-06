package com.haydeproductions.project.rule.condition.metadata;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.operator.TimeComparisonOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ModifiedTimeConditionTest {

    @TempDir
    Path tempDir;

    private static final Instant FILE_TIME =
            Instant.parse("2025-01-10T12:00:00Z");

    @Test
    void supportsAbsoluteTimeComparisons() throws Exception {
        FileContext file = fileAt(FILE_TIME);

        assertTrue(new ModifiedTimeCondition(
                TimeComparisonOperator.BEFORE,
                FILE_TIME.plusSeconds(1)
        ).matches(file));

        assertTrue(new ModifiedTimeCondition(
                TimeComparisonOperator.BEFORE_OR_EQUAL,
                FILE_TIME
        ).matches(file));

        assertTrue(new ModifiedTimeCondition(
                TimeComparisonOperator.EQUAL,
                FILE_TIME
        ).matches(file));

        assertTrue(new ModifiedTimeCondition(
                TimeComparisonOperator.AFTER_OR_EQUAL,
                FILE_TIME
        ).matches(file));

        assertTrue(new ModifiedTimeCondition(
                TimeComparisonOperator.AFTER,
                FILE_TIME.minusSeconds(1)
        ).matches(file));
    }

    @Test
    void olderThanUsesInjectedClock() throws Exception {
        Clock clock = Clock.fixed(
                Instant.parse("2025-01-20T12:00:00Z"),
                ZoneOffset.UTC
        );

        assertTrue(
                ModifiedTimeCondition.olderThan(
                        Duration.ofDays(5),
                        clock
                ).matches(fileAt(FILE_TIME))
        );
    }

    @Test
    void newerThanUsesInjectedClock() throws Exception {
        Clock clock = Clock.fixed(
                Instant.parse("2025-01-11T12:00:00Z"),
                ZoneOffset.UTC
        );

        assertTrue(
                ModifiedTimeCondition.newerThan(
                        Duration.ofDays(2),
                        clock
                ).matches(fileAt(FILE_TIME))
        );
    }

    @Test
    void relativeBoundaryIsStrict() throws Exception {
        Clock clock = Clock.fixed(
                Instant.parse("2025-01-20T12:00:00Z"),
                ZoneOffset.UTC
        );

        assertFalse(
                ModifiedTimeCondition.olderThan(
                        Duration.ofDays(10),
                        clock
                ).matches(fileAt(FILE_TIME))
        );

        assertFalse(
                ModifiedTimeCondition.newerThan(
                        Duration.ofDays(10),
                        clock
                ).matches(fileAt(FILE_TIME))
        );
    }

    @Test
    void rejectsNegativeAge() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModifiedTimeCondition.olderThan(
                        Duration.ofSeconds(-1)
                )
        );
    }

    @Test
    void ioFailureBecomesConditionEvaluationException() {
        FileContext missing = new FileContext(
                tempDir.resolve("missing.txt")
        );

        ConditionEvaluationException exception = assertThrows(
                ConditionEvaluationException.class,
                () -> new ModifiedTimeCondition(
                        TimeComparisonOperator.AFTER,
                        Instant.EPOCH
                ).matches(missing)
        );

        assertNotNull(exception.getCause());
    }

    private FileContext fileAt(Instant instant) throws Exception {
        Path file = tempDir.resolve("file-" + Math.abs(instant.hashCode()) + ".txt");
        Files.writeString(file, "test");
        Files.setLastModifiedTime(file, FileTime.from(instant));
        return new FileContext(file);
    }
}
