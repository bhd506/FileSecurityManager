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
        RuleSet ruleSet = config.getRuleSets().get(0);

        assertTrue(ruleSet.isFileOwned(root.resolve("direct.txt")));
        assertFalse(ruleSet.isFileOwned(root.resolve("nested/file.txt")));
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
        RuleSet ruleSet = config.getRuleSets().get(0);

        assertTrue(ruleSet.isFileOwned(deep));
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
        RuleSet ruleSet = config.getRuleSets().get(0);

        assertFalse(ruleSet.isFileOwned(root.resolve("direct.txt")));
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
        RuleSet parentRuleSet = config.getRuleSets().get(0);

        assertTrue(parentRuleSet.isFileOwned(visible));
        assertFalse(parentRuleSet.isFileOwned(hidden));
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
        RuleSet ruleSet = config.getRuleSets().get(0);

        assertTrue(ruleSet.isFileOwned(privateFile));
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

        assertEquals(root.resolve("first").toAbsolutePath().normalize(), ruleSets.get(0).getRoot());
        assertEquals(root.resolve("second").toAbsolutePath().normalize(), ruleSets.get(1).getRoot());

        assertTrue(ruleSets.get(0).isFileOwned(firstFile));
        assertFalse(ruleSets.get(0).isFileOwned(secondFile));
        assertTrue(ruleSets.get(1).isFileOwned(secondFile));
        assertFalse(ruleSets.get(1).isFileOwned(firstFile));
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


    @Test
    void statusApiDefaultsToDisabled() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("{}\n");

        Config config = ConfigLoader.load(yaml, root);

        assertEquals(
                StatusApiConfig.disabled(),
                config.getStatusApi()
        );
    }

    @Test
    void loadsEnabledStatusApiConfiguration() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                statusApi:
                  enabled: true
                  host: "0.0.0.0"
                  port: 9090
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertEquals(
                new StatusApiConfig(true, "0.0.0.0", 9090),
                config.getStatusApi()
        );
    }

    @Test
    void statusApiUsesDefaultHostAndPortWhenOmitted() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                statusApi:
                  enabled: true
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertEquals(
                new StatusApiConfig(true, "127.0.0.1", 8080),
                config.getStatusApi()
        );
    }

    @Test
    void statusApiAllowsEphemeralPortZero() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                statusApi:
                  enabled: true
                  port: 0
                """);

        Config config = ConfigLoader.load(yaml, root);

        assertEquals(0, config.getStatusApi().port());
    }

    @Test
    void statusApiRejectsBlankHost() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                statusApi:
                  enabled: true
                  host: " "
                """);

        assertThrows(
                ConfigException.class,
                () -> ConfigLoader.load(yaml, root)
        );
    }

    @Test
    void statusApiRejectsInvalidPort() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                statusApi:
                  enabled: true
                  port: 70000
                """);

        assertThrows(
                ConfigException.class,
                () -> ConfigLoader.load(yaml, root)
        );
    }

    @Test
    void unknownStatusApiFieldIsRejected() throws IOException {
        Path root = createRoot();
        Path yaml = writeYaml("""
                statusApi:
                  enabled: true
                  unknown: true
                """);

        assertThrows(
                IOException.class,
                () -> ConfigLoader.load(yaml, root)
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
