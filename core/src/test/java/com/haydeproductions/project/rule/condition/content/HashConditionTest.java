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

class HashConditionTest {

    @TempDir
    Path tempDir;

    private static final String HELLO_HASH =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e"
                    + "1b161e5c1fa7425e73043362938b9824";

    @Test
    void matchesExactSha256() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "hello");

        assertTrue(
                new HashCondition(HELLO_HASH)
                        .matches(new FileContext(file))
        );
    }

    @Test
    void configuredHashIsCaseInsensitive() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "hello");

        assertTrue(
                new HashCondition(HELLO_HASH.toUpperCase())
                        .matches(new FileContext(file))
        );
    }

    @Test
    void matchesAnyHashInSet() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "hello");

        String other = "0".repeat(64);
        HashCondition condition = new HashCondition(
                Set.of(other, HELLO_HASH)
        );

        assertTrue(condition.matches(new FileContext(file)));
    }

    @Test
    void notInInvertsHashMembership() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "hello");

        HashCondition condition = new HashCondition(
                List.of(HELLO_HASH),
                MembershipOperator.NOT_IN
        );

        assertFalse(condition.matches(new FileContext(file)));
    }

    @Test
    void rejectsMalformedHash() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HashCondition("not-a-hash")
        );
    }

    @Test
    void rejectsEmptyHashCollection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HashCondition(Set.of())
        );
    }

    @Test
    void hashSetIsImmutable() {
        HashCondition condition = new HashCondition(HELLO_HASH);

        assertThrows(
                UnsupportedOperationException.class,
                () -> condition.getSha256Hashes().add("0".repeat(64))
        );
    }

    @Test
    void ioFailureBecomesConditionEvaluationException() {
        FileContext missing = new FileContext(
                tempDir.resolve("missing.txt")
        );

        assertThrows(
                ConditionEvaluationException.class,
                () -> new HashCondition(HELLO_HASH).matches(missing)
        );
    }
}
