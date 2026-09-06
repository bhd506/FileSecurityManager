package com.haydeproductions.project.config;

import com.haydeproductions.project.scope.RuleSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsEmptyConfiguration() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("{}");

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(config.getRuleSets().isEmpty());
        assertEquals(root.toAbsolutePath().normalize(), config.getRoot());
    }

    @Test
    void loadsExplicitEmptyRuleSetList() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("ruleSets: []\n");

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(config.getRuleSets().isEmpty());
    }

    @Test
    void loadsSingleRuleSet() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "."
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertEquals(1, config.getRuleSets().size());
    }

    @Test
    void loadsMultipleRuleSets() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "."
                  - path: "private"
                  - path: "private/uploads"
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertEquals(3, config.getRuleSets().size());
    }

    @Test
    void rootIsConvertedToAbsoluteNormalizedPath() throws IOException {
        Path root = tempDir.resolve("one/../data");
        Files.createDirectories(tempDir.resolve("data"));
        Path yaml = writeYaml("{}");

        Config config = ConfigLoader.load(yaml, root);

        assertEquals(
                root.toAbsolutePath().normalize(),
                config.getRoot()
        );
    }

    @Test
    void missingRuleSetPathDefaultsToRoot() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - override: true
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(config.getOverrides().isOverride(config.getRoot()));
    }

    @Test
    void blankRuleSetPathDefaultsToRoot() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "   "
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(config.getOverrides().isOverride(config.getRoot()));
    }

    @Test
    void relativeRuleSetPathIsResolvedAgainstRoot() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "private/uploads"
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(
                config.getOverrides().isOverride(
                        config.getRoot().resolve("private/uploads")
                )
        );
    }

    @Test
    void resolvedRuleSetPathIsNormalized() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "private/../public"
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(
                config.getOverrides().isOverride(
                        config.getRoot().resolve("public")
                )
        );
    }

    @Test
    void overrideTrueRegistersRuleSetPath() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "private"
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(
                config.getOverrides().isOverride(
                        config.getRoot().resolve("private")
                )
        );
    }

    @Test
    void overrideFalseDoesNotRegisterRuleSetPath() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "public"
                    override: false
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertFalse(
                config.getOverrides().isOverride(
                        config.getRoot().resolve("public")
                )
        );
    }

    @Test
    void omittedOverrideDefaultsToFalse() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "public"
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertFalse(
                config.getOverrides().isOverride(
                        config.getRoot().resolve("public")
                )
        );
    }

    @Test
    void multipleOverridesAreAllRegistered() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "first"
                    override: true
                  - path: "second"
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(config.getOverrides().isOverride(config.getRoot().resolve("first")));
        assertTrue(config.getOverrides().isOverride(config.getRoot().resolve("second")));
    }

    @Test
    void duplicateOverridePathsAreCollapsedByRegistrySet() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "private"
                    override: true
                  - path: "private"
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);
        String output = captureStdout(config.getOverrides()::logOverrides);

        long occurrences = output.lines()
                .filter(line -> line.equals(config.getRoot().resolve("private").toString()))
                .count();

        assertEquals(1, occurrences);
    }

    @Test
    void configuredMaxDepthIsAppliedToConstructedRuleSet() throws IOException {
        Path root = createRoot();
        Files.writeString(root.resolve("direct.txt"), "test");
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/file.txt"), "test");

        Path yaml = writeYaml("""
                ruleSets:
                  - path: "."
                    maxDepth: 1
                """);

        Config config = ConfigLoader.load(yaml, root);
        String output = applyAndCapture(config.getRuleSets().get(0));

        assertTrue(output.contains(root.resolve("direct.txt").toString()));
        assertFalse(output.contains(root.resolve("nested/file.txt").toString()));
    }

    @Test
    void omittedMaxDepthDefaultsToUnlimitedTraversal() throws IOException {
        Path root = createRoot();
        Files.createDirectories(root.resolve("one/two/three"));
        Path deep = root.resolve("one/two/three/deep.txt");
        Files.writeString(deep, "test");

        Path yaml = writeYaml("""
                ruleSets:
                  - path: "."
                """);

        Config config = ConfigLoader.load(yaml, root);
        String output = applyAndCapture(config.getRuleSets().get(0));

        assertTrue(output.contains(deep.toString()));
    }

    @Test
    void maxDepthZeroResultsInNoFilesBeingAppliedForDirectoryRoot() throws IOException {
        Path root = createRoot();
        Files.writeString(root.resolve("direct.txt"), "test");

        Path yaml = writeYaml("""
                ruleSets:
                  - path: "."
                    maxDepth: 0
                """);

        Config config = ConfigLoader.load(yaml, root);
        String output = applyAndCapture(config.getRuleSets().get(0));

        assertTrue(output.isBlank());
    }

    @Test
    void loadedOverrideRegistryActuallyStopsParentRuleSetTraversal() throws IOException {
        Path root = createRoot();
        Path visible = root.resolve("visible.txt");
        Files.writeString(visible, "test");
        Files.createDirectories(root.resolve("private"));
        Path hidden = root.resolve("private/hidden.txt");
        Files.writeString(hidden, "test");

        Path yaml = writeYaml("""
                ruleSets:
                  - path: "."
                  - path: "private"
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);
        String parentOutput = applyAndCapture(config.getRuleSets().get(0));

        assertTrue(parentOutput.contains(visible.toString()));
        assertFalse(parentOutput.contains(hidden.toString()));
    }

    @Test
    void loadedOverrideRuleSetCanStillTraverseItsOwnRoot() throws IOException {
        Path root = createRoot();
        Files.createDirectories(root.resolve("private"));
        Path privateFile = root.resolve("private/file.txt");
        Files.writeString(privateFile, "test");

        Path yaml = writeYaml("""
                ruleSets:
                  - path: "private"
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);
        String output = applyAndCapture(config.getRuleSets().get(0));

        assertTrue(output.contains(privateFile.toString()));
    }

    @Test
    void ruleSetOrderMatchesYamlOrder() throws IOException {
        Path root = createRoot();
        Files.createDirectories(root.resolve("first"));
        Files.createDirectories(root.resolve("second"));
        Path firstFile = root.resolve("first/first.txt");
        Path secondFile = root.resolve("second/second.txt");
        Files.writeString(firstFile, "test");
        Files.writeString(secondFile, "test");

        Path yaml = writeYaml("""
                ruleSets:
                  - path: "first"
                  - path: "second"
                """);

        Config config = ConfigLoader.load(yaml, root);
        List<RuleSet> ruleSets = config.getRuleSets();

        String firstOutput = applyAndCapture(ruleSets.get(0));
        String secondOutput = applyAndCapture(ruleSets.get(1));

        assertTrue(firstOutput.contains(firstFile.toString()));
        assertFalse(firstOutput.contains(secondFile.toString()));
        assertTrue(secondOutput.contains(secondFile.toString()));
        assertFalse(secondOutput.contains(firstFile.toString()));
    }

    @Test
    void malformedYamlThrowsIOException() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("ruleSets: [\n");

        assertThrows(IOException.class, () -> ConfigLoader.load(yaml, root));
    }

    @Test
    void missingConfigFileThrowsIOException() throws IOException {
        Path root = createRoot();
        Path missing = tempDir.resolve("missing.yaml");

        assertThrows(IOException.class, () -> ConfigLoader.load(missing, root));
    }


    @Test
    void ruleSetPathCannotEscapeConfiguredRoot() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "../outside"
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigLoader.load(yaml, root)
        );
    }

    @Test
    void deeplyNestedTraversalCannotEscapeConfiguredRoot() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "safe/../../outside"
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigLoader.load(yaml, root)
        );
    }

    @Test
    void normalizedRuleSetPathThatRemainsInsideRootIsAllowed() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "safe/../inside"
                    override: true
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertTrue(
                config.getOverrides().isOverride(
                        root.resolve("inside")
                )
        );
    }

    private Path createRoot() throws IOException {
        Path root = tempDir.resolve("data");
        Files.createDirectories(root);
        return root;
    }

    private Path writeYaml(String yaml) throws IOException {
        Path file = tempDir.resolve("config-" + System.nanoTime() + ".yaml");
        Files.writeString(file, yaml);
        return file;
    }

    private String applyAndCapture(RuleSet ruleSet) throws IOException {
        final IOException[] thrown = new IOException[1];

        String output = captureStdout(() -> {
            try {
                ruleSet.applyRules();
            } catch (IOException e) {
                thrown[0] = e;
            }
        });

        if (thrown[0] != null) {
            throw thrown[0];
        }

        return output;
    }

    private String captureStdout(Runnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(originalOut);
        }

        return output.toString(StandardCharsets.UTF_8);
    }
}
