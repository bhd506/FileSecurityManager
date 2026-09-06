package com.haydeproductions.project.rule.condition.path;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.operator.TextMatchOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathConditionTest {

    @TempDir
    Path tempDir;

    @Test
    void matchesPathRelativeToConfiguredBaseRoot() throws Exception {
        Path root = tempDir.resolve("data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.EQUALS,
                "private/file.txt"
        );

        assertTrue(condition.matches(
                new FileContext(root.resolve("private/file.txt"))
        ));
    }

    @Test
    void pathComparisonDoesNotExposeAbsoluteDeploymentPath() throws Exception {
        Path root = tempDir.resolve("some/deployment/data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.STARTS_WITH,
                "uploads/"
        );

        assertTrue(condition.matches(
                new FileContext(root.resolve("uploads/a.txt"))
        ));
    }

    @Test
    void fileOutsideBaseRootNeverMatches() throws Exception {
        Path root = tempDir.resolve("data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.GLOB,
                "**"
        );

        assertFalse(condition.matches(
                new FileContext(tempDir.resolve("other/file.txt"))
        ));
    }

    @Test
    void globSupportsRecursiveDirectories() throws Exception {
        Path root = tempDir.resolve("data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.GLOB,
                "uploads/**/*.zip"
        );

        assertTrue(condition.matches(
                new FileContext(root.resolve("uploads/direct.zip"))
        ));

        assertTrue(condition.matches(
                new FileContext(root.resolve("uploads/a/b/deep.zip"))
        ));

        assertFalse(condition.matches(
                new FileContext(root.resolve("uploads/a/b/deep.txt"))
        ));
    }

    @Test
    void singleStarDoesNotCrossDirectoryBoundary() throws Exception {
        Path root = tempDir.resolve("data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.GLOB,
                "uploads/*.zip"
        );

        assertTrue(condition.matches(
                new FileContext(root.resolve("uploads/direct.zip"))
        ));

        assertFalse(condition.matches(
                new FileContext(root.resolve("uploads/nested/deep.zip"))
        ));
    }

    @Test
    void pathsAreNormalizedBeforeRelativizing() throws Exception {
        Path root = tempDir.resolve("data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.EQUALS,
                "file.txt"
        );

        assertTrue(condition.matches(
                new FileContext(root.resolve("folder/../file.txt"))
        ));
    }

    @Test
    void pathMatchingIsCaseSensitiveByDefault() throws Exception {
        Path root = tempDir.resolve("data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.EQUALS,
                "FILE.TXT"
        );

        assertFalse(condition.matches(
                new FileContext(root.resolve("file.txt"))
        ));
    }

    @Test
    void caseInsensitivePathMatchingCanBeRequested() throws Exception {
        Path root = tempDir.resolve("data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.EQUALS,
                "PRIVATE/FILE.TXT",
                false
        );

        assertTrue(condition.matches(
                new FileContext(root.resolve("private/file.txt"))
        ));
    }

    @Test
    void baseRootIsNormalizedAtConstruction() {
        Path root = tempDir.resolve("one/../data");
        PathCondition condition = new PathCondition(
                root,
                TextMatchOperator.EQUALS,
                "file.txt"
        );

        assertEquals(
                root.toAbsolutePath().normalize(),
                condition.getBaseRoot()
        );
    }
}
