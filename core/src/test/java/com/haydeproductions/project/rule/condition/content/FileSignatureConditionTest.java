package com.haydeproductions.project.rule.condition.content;

import com.haydeproductions.project.file.FileSignature;
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

class FileSignatureConditionTest {

    @TempDir
    Path tempDir;

    @Test
    void matchesDetectedSignature() throws Exception {
        Path file = pdfFile();

        assertTrue(
                new FileSignatureCondition(FileSignature.PDF)
                        .matches(new FileContext(file))
        );
    }

    @Test
    void matchesAnyConfiguredSignature() throws Exception {
        Path file = pdfFile();

        FileSignatureCondition condition = new FileSignatureCondition(
                Set.of(FileSignature.PNG, FileSignature.PDF)
        );

        assertTrue(condition.matches(new FileContext(file)));
    }

    @Test
    void notInInvertsSignatureMembership() throws Exception {
        Path file = pdfFile();

        FileSignatureCondition condition = new FileSignatureCondition(
                List.of(FileSignature.PDF),
                MembershipOperator.NOT_IN
        );

        assertFalse(condition.matches(new FileContext(file)));
    }

    @Test
    void unknownSignatureCanBeMatchedExplicitly() throws Exception {
        Path file = tempDir.resolve("plain.bin");
        Files.writeString(file, "plain text without a known magic header");

        assertTrue(
                new FileSignatureCondition(FileSignature.UNKNOWN)
                        .matches(new FileContext(file))
        );
    }

    @Test
    void rejectsEmptySignatureCollection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileSignatureCondition(Set.of())
        );
    }

    @Test
    void rejectsNullSignatureElement() {
        java.util.ArrayList<FileSignature> values = new java.util.ArrayList<>();
        values.add(FileSignature.PDF);
        values.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new FileSignatureCondition(values)
        );
    }

    @Test
    void ioFailureBecomesConditionEvaluationException() {
        FileContext missing = new FileContext(
                tempDir.resolve("missing.bin")
        );

        assertThrows(
                ConditionEvaluationException.class,
                () -> new FileSignatureCondition(FileSignature.PDF)
                        .matches(missing)
        );
    }

    private Path pdfFile() throws Exception {
        Path file = tempDir.resolve("document.bin");
        Files.write(file, "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return file;
    }
}
