package com.haydeproductions.project.rule.condition.metadata;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.operator.LongComparisonOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SizeConditionTest {

    @TempDir
    Path tempDir;

    @Test
    void supportsAllComparisonOperators() throws Exception {
        FileContext file = fileWithSize(10);

        assertTrue(condition(LongComparisonOperator.EQUAL, 10).matches(file));
        assertTrue(condition(LongComparisonOperator.NOT_EQUAL, 9).matches(file));
        assertTrue(condition(LongComparisonOperator.LESS_THAN, 11).matches(file));
        assertTrue(condition(LongComparisonOperator.LESS_THAN_OR_EQUAL, 10).matches(file));
        assertTrue(condition(LongComparisonOperator.GREATER_THAN, 9).matches(file));
        assertTrue(condition(LongComparisonOperator.GREATER_THAN_OR_EQUAL, 10).matches(file));
    }

    @Test
    void comparisonReturnsFalseWhenRequirementIsNotMet() throws Exception {
        FileContext file = fileWithSize(10);

        assertFalse(condition(LongComparisonOperator.GREATER_THAN, 10).matches(file));
        assertFalse(condition(LongComparisonOperator.LESS_THAN, 10).matches(file));
    }

    @Test
    void betweenInclusiveIncludesBothBounds() throws Exception {
        assertTrue(SizeCondition.betweenInclusive(5, 10)
                .matches(fileWithSize(5)));
        assertTrue(SizeCondition.betweenInclusive(5, 10)
                .matches(fileWithSize(10)));
        assertFalse(SizeCondition.betweenInclusive(5, 10)
                .matches(fileWithSize(11)));
    }

    @Test
    void rejectsNegativeComparisonSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> condition(LongComparisonOperator.EQUAL, -1)
        );
    }

    @Test
    void rejectsInvalidBetweenRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SizeCondition.betweenInclusive(10, 5)
        );
    }

    @Test
    void ioFailureBecomesConditionEvaluationException() {
        FileContext missing = new FileContext(
                tempDir.resolve("missing.txt")
        );

        ConditionEvaluationException exception = assertThrows(
                ConditionEvaluationException.class,
                () -> condition(LongComparisonOperator.EQUAL, 0)
                        .matches(missing)
        );

        assertNotNull(exception.getCause());
    }

    private SizeCondition condition(
            LongComparisonOperator operator,
            long size
    ) {
        return new SizeCondition(operator, size);
    }

    private FileContext fileWithSize(int size) throws Exception {
        Path file = tempDir.resolve("file-" + size + ".bin");
        Files.write(file, new byte[size]);
        return new FileContext(file);
    }
}
