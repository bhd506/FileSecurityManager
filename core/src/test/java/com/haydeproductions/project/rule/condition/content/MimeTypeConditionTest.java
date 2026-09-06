package com.haydeproductions.project.rule.condition.content;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MimeTypeConditionTest {

    @TempDir
    Path tempDir;

    @Test
    void matchesExactMimeTypeFromContentSignature() throws Exception {
        Path file = pngFile("picture.txt");

        assertTrue(
                new MimeTypeCondition("image/png")
                        .matches(new FileContext(file))
        );
    }

    @Test
    void mimeMatchingIsCaseInsensitive() throws Exception {
        Path file = pngFile("picture.bin");

        assertTrue(
                new MimeTypeCondition("IMAGE/PNG")
                        .matches(new FileContext(file))
        );
    }

    @Test
    void supportsTopLevelWildcard() throws Exception {
        Path file = pngFile("picture.bin");

        assertTrue(
                new MimeTypeCondition("image/*")
                        .matches(new FileContext(file))
        );

        assertFalse(
                new MimeTypeCondition("application/*")
                        .matches(new FileContext(file))
        );
    }

    @Test
    void supportsUniversalWildcard() throws Exception {
        Path file = pngFile("picture.bin");

        assertTrue(
                new MimeTypeCondition("*/*")
                        .matches(new FileContext(file))
        );
    }

    @Test
    void matchesAnyConfiguredMimePattern() throws Exception {
        Path file = pngFile("picture.bin");

        MimeTypeCondition condition = new MimeTypeCondition(
                Set.of("application/pdf", "image/*")
        );

        assertTrue(condition.matches(new FileContext(file)));
    }

    @Test
    void notInInvertsMimeMembership() throws Exception {
        Path file = pngFile("picture.bin");

        MimeTypeCondition condition = new MimeTypeCondition(
                List.of("image/*"),
                MembershipOperator.NOT_IN
        );

        assertFalse(condition.matches(new FileContext(file)));
    }

    @Test
    void rejectsInvalidMimePattern() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MimeTypeCondition("not-a-mime")
        );
    }

    @Test
    void rejectsEmptyMimeCollection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MimeTypeCondition(Set.of())
        );
    }

    @Test
    void ioFailureBecomesConditionEvaluationException() {
        FileContext missing = new FileContext(
                tempDir.resolve("missing.bin")
        );

        assertThrows(
                ConditionEvaluationException.class,
                () -> new MimeTypeCondition("image/png")
                        .matches(missing)
        );
    }

    private Path pngFile(String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(
                file,
                new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );
        return file;
    }
}
