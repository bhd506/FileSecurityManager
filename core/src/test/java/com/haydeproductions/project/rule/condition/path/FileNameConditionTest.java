package com.haydeproductions.project.rule.condition.path;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.operator.TextMatchOperator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileNameConditionTest {

    @Test
    void equalsMatchesExactName() throws Exception {
        assertTrue(condition(TextMatchOperator.EQUALS, "file.txt")
                .matches(file("file.txt")));
        assertFalse(condition(TextMatchOperator.EQUALS, "file.txt")
                .matches(file("other.txt")));
    }

    @Test
    void notEqualsWorks() throws Exception {
        assertTrue(condition(TextMatchOperator.NOT_EQUALS, "blocked.txt")
                .matches(file("allowed.txt")));
        assertFalse(condition(TextMatchOperator.NOT_EQUALS, "blocked.txt")
                .matches(file("blocked.txt")));
    }

    @Test
    void startsWithWorks() throws Exception {
        assertTrue(condition(TextMatchOperator.STARTS_WITH, "backup-")
                .matches(file("backup-123.zip")));
    }

    @Test
    void endsWithWorks() throws Exception {
        assertTrue(condition(TextMatchOperator.ENDS_WITH, ".tmp")
                .matches(file("thing.tmp")));
    }

    @Test
    void containsWorks() throws Exception {
        assertTrue(condition(TextMatchOperator.CONTAINS, "secret")
                .matches(file("my-secret-file.txt")));
    }

    @Test
    void globWorks() throws Exception {
        FileNameCondition condition = condition(
                TextMatchOperator.GLOB,
                "backup-??.zip"
        );

        assertTrue(condition.matches(file("backup-12.zip")));
        assertFalse(condition.matches(file("backup-123.zip")));
    }

    @Test
    void regexWorks() throws Exception {
        FileNameCondition condition = condition(
                TextMatchOperator.REGEX,
                "backup-[0-9]+\\.zip"
        );

        assertTrue(condition.matches(file("backup-123.zip")));
        assertFalse(condition.matches(file("backup-abc.zip")));
    }

    @Test
    void matchingIsCaseSensitiveByDefault() throws Exception {
        assertFalse(condition(TextMatchOperator.EQUALS, "FILE.TXT")
                .matches(file("file.txt")));
    }

    @Test
    void caseInsensitiveMatchingCanBeRequested() throws Exception {
        FileNameCondition condition = new FileNameCondition(
                TextMatchOperator.EQUALS,
                "FILE.TXT",
                false
        );

        assertTrue(condition.matches(file("file.txt")));
    }

    @Test
    void invalidRegexIsRejectedAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> condition(TextMatchOperator.REGEX, "[")
        );
    }

    private FileNameCondition condition(
            TextMatchOperator operator,
            String pattern
    ) {
        return new FileNameCondition(operator, pattern);
    }

    private FileContext file(String name) {
        return new FileContext(Path.of(name));
    }
}
