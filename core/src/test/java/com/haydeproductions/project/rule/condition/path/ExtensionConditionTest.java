package com.haydeproductions.project.rule.condition.path;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionConditionTest {

    @Test
    void matchesSingleExtension() throws Exception {
        assertTrue(
                new ExtensionCondition("txt")
                        .matches(file("file.txt"))
        );
    }

    @Test
    void acceptsLeadingDotInConfiguredExtension() throws Exception {
        assertTrue(
                new ExtensionCondition(".txt")
                        .matches(file("file.txt"))
        );
    }

    @Test
    void defaultMatchingIsCaseInsensitive() throws Exception {
        assertTrue(
                new ExtensionCondition("EXE")
                        .matches(file("payload.ExE"))
        );
    }

    @Test
    void optionalCaseSensitiveMatchingIsSupported() throws Exception {
        ExtensionCondition condition = new ExtensionCondition(
                Set.of("EXE"),
                MembershipOperator.IN,
                true
        );

        assertTrue(condition.matches(file("payload.EXE")));
        assertFalse(condition.matches(file("payload.exe")));
    }

    @Test
    void matchesAnyConfiguredExtension() throws Exception {
        ExtensionCondition condition = new ExtensionCondition(
                Set.of("exe", "dll", "bat")
        );

        assertTrue(condition.matches(file("thing.dll")));
        assertFalse(condition.matches(file("thing.txt")));
    }

    @Test
    void matchesMultipartSuffix() throws Exception {
        ExtensionCondition condition = new ExtensionCondition("tar.gz");

        assertTrue(condition.matches(file("archive.tar.gz")));
        assertFalse(condition.matches(file("archive.gz")));
    }

    @Test
    void lastExtensionAlsoMatchesMultipartFile() throws Exception {
        assertTrue(
                new ExtensionCondition("gz")
                        .matches(file("archive.tar.gz"))
        );
    }

    @Test
    void hiddenFileWithoutExtensionDoesNotMatch() throws Exception {
        assertFalse(
                new ExtensionCondition("bashrc")
                        .matches(file(".bashrc"))
        );
    }

    @Test
    void notInInvertsMembership() throws Exception {
        ExtensionCondition condition = new ExtensionCondition(
                List.of("exe", "dll"),
                MembershipOperator.NOT_IN,
                false
        );

        assertFalse(condition.matches(file("thing.exe")));
        assertTrue(condition.matches(file("thing.txt")));
    }

    @Test
    void configuredExtensionSetIsImmutable() {
        ExtensionCondition condition = new ExtensionCondition("txt");

        assertThrows(
                UnsupportedOperationException.class,
                () -> condition.getExtensions().add("exe")
        );
    }

    @Test
    void rejectsEmptyExtensionCollection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExtensionCondition(Set.of())
        );
    }

    @Test
    void rejectsBlankExtension() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExtensionCondition("   ")
        );
    }

    private FileContext file(String name) {
        return new FileContext(Path.of(name));
    }
}
