package com.haydeproductions.project.scope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuleSetTest {

    @TempDir
    Path root;

    @Test
    void builderCreatesRuleSet() {
        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).build();

        assertNotNull(ruleSet);
    }

    @Test
    void defaultMaxDepthTraversesAllSubdirectories() throws IOException {
        Path rootFile = createFile("root.txt");
        Path nestedFile = createFile("one/nested.txt");
        Path deepFile = createFile("one/two/three/deep.txt");

        RecordingRuleSet ruleSet = ruleSet(root);
        ruleSet.applyRules();

        assertEquals(Set.of(rootFile, nestedFile, deepFile), ruleSet.visited());
    }

    @Test
    void maxDepthZeroProcessesNoFilesBelowDirectoryRoot() throws IOException {
        createFile("root.txt");
        createFile("folder/nested.txt");

        RecordingRuleSet ruleSet = ruleSet(root, 0);
        ruleSet.applyRules();

        assertTrue(ruleSet.visited().isEmpty());
    }

    @Test
    void maxDepthOneProcessesOnlyFilesDirectlyInsideRoot() throws IOException {
        Path direct = createFile("direct.txt");
        createFile("folder/nested.txt");
        createFile("folder/deeper/deep.txt");

        RecordingRuleSet ruleSet = ruleSet(root, 1);
        ruleSet.applyRules();

        assertEquals(Set.of(direct), ruleSet.visited());
    }

    @Test
    void maxDepthTwoProcessesDirectFilesAndOneSubdirectoryLevel() throws IOException {
        Path direct = createFile("direct.txt");
        Path nested = createFile("folder/nested.txt");
        createFile("folder/deeper/deep.txt");

        RecordingRuleSet ruleSet = ruleSet(root, 2);
        ruleSet.applyRules();

        assertEquals(Set.of(direct, nested), ruleSet.visited());
    }

    @Test
    void directoriesAreNotPassedToApplyRule() throws IOException {
        Path file = createFile("file.txt");
        Files.createDirectories(root.resolve("empty-directory"));
        Files.createDirectories(root.resolve("nested/another-empty-directory"));

        RecordingRuleSet ruleSet = ruleSet(root);
        ruleSet.applyRules();

        assertEquals(Set.of(file), ruleSet.visited());
    }

    @Test
    void emptyDirectoryProducesNoAppliedFiles() throws IOException {
        RecordingRuleSet ruleSet = ruleSet(root);
        ruleSet.applyRules();

        assertTrue(ruleSet.visited().isEmpty());
    }

    @Test
    void overrideDirectoryStopsTraversalIntoThatSubtree() throws IOException {
        Path rootFile = createFile("root.txt");
        Path publicFile = createFile("public/file.txt");
        createFile("private/private.txt");
        createFile("private/deeper/deep.txt");

        Path privateRoot = root.resolve("private");
        RecordingRuleSet ruleSet = new RecordingRuleSet(
                root,
                Integer.MAX_VALUE,
                new OverrideRegistry(Set.of(privateRoot))
        );

        ruleSet.applyRules();

        assertEquals(Set.of(rootFile, publicFile), ruleSet.visited());
    }

    @Test
    void multipleOverrideDirectoriesEachStopTheirSubtree() throws IOException {
        Path visible = createFile("visible.txt");
        createFile("private/a.txt");
        createFile("restricted/b.txt");

        OverrideRegistry registry = new OverrideRegistry(Set.of(
                root.resolve("private"),
                root.resolve("restricted")
        ));

        RecordingRuleSet ruleSet = new RecordingRuleSet(
                root,
                Integer.MAX_VALUE,
                registry
        );

        ruleSet.applyRules();

        assertEquals(Set.of(visible), ruleSet.visited());
    }

    @Test
    void ruleSetDoesNotSkipItsOwnRootWhenRootIsAnOverride() throws IOException {
        Path overrideRoot = root.resolve("private");
        Path direct = createFile("private/direct.txt");
        Path nested = createFile("private/nested/file.txt");

        RecordingRuleSet ruleSet = new RecordingRuleSet(
                overrideRoot,
                Integer.MAX_VALUE,
                new OverrideRegistry(Set.of(overrideRoot))
        );

        ruleSet.applyRules();

        assertEquals(Set.of(direct, nested), ruleSet.visited());
    }

    @Test
    void lowerOverrideStillStopsRuleSetWhoseOwnRootIsAnOverride() throws IOException {
        Path ownRoot = root.resolve("private");
        Path visible = createFile("private/visible.txt");
        createFile("private/deeper/hidden.txt");

        OverrideRegistry registry = new OverrideRegistry(Set.of(
                ownRoot,
                ownRoot.resolve("deeper")
        ));

        RecordingRuleSet ruleSet = new RecordingRuleSet(
                ownRoot,
                Integer.MAX_VALUE,
                registry
        );

        ruleSet.applyRules();

        assertEquals(Set.of(visible), ruleSet.visited());
    }

    @Test
    void overrideOutsideCurrentScopeHasNoEffect() throws IOException {
        Path file = createFile("file.txt");

        RecordingRuleSet ruleSet = new RecordingRuleSet(
                root,
                Integer.MAX_VALUE,
                new OverrideRegistry(Set.of(root.getParent().resolve("elsewhere")))
        );

        ruleSet.applyRules();

        assertEquals(Set.of(file), ruleSet.visited());
    }

    @Test
    void maxDepthStillAppliesWhenOverrideRegistryIsPresent() throws IOException {
        Path direct = createFile("direct.txt");
        createFile("normal/nested.txt");
        createFile("private/hidden.txt");

        RecordingRuleSet ruleSet = new RecordingRuleSet(
                root,
                1,
                new OverrideRegistry(Set.of(root.resolve("private")))
        );

        ruleSet.applyRules();

        assertEquals(Set.of(direct), ruleSet.visited());
    }

    @Test
    void nonexistentRootThrowsNoSuchFileException() {
        RecordingRuleSet ruleSet = ruleSet(root.resolve("missing"));

        assertThrows(NoSuchFileException.class, ruleSet::applyRules);
    }

    @Test
    void negativeMaxDepthThrowsIllegalArgumentException() {
        RecordingRuleSet ruleSet = ruleSet(root, -1);

        assertThrows(IllegalArgumentException.class, ruleSet::applyRules);
    }

    private RecordingRuleSet ruleSet(Path path) {
        return new RecordingRuleSet(
                path,
                Integer.MAX_VALUE,
                new OverrideRegistry(Set.of())
        );
    }

    private RecordingRuleSet ruleSet(Path path, int maxDepth) {
        return new RecordingRuleSet(
                path,
                maxDepth,
                new OverrideRegistry(Set.of())
        );
    }

    private Path createFile(String relativePath) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "test");
        return file;
    }

    private static final class RecordingRuleSet extends RuleSet {

        private final Set<Path> visited = new HashSet<>();

        RecordingRuleSet(
                Path root,
                int maxDepth,
                OverrideRegistry overrideRegistry
        ) {
            super(
                    RuleSet.builder(root, overrideRegistry)
                            .maxDepth(maxDepth)
            );
        }

        @Override
        public void applyRule(Path file) {
            visited.add(file);
        }

        Set<Path> visited() {
            return Set.copyOf(visited);
        }
    }
}
