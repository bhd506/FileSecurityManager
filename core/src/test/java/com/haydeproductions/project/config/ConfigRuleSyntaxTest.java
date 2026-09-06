package com.haydeproductions.project.config;

import com.haydeproductions.project.file.FileFingerprintService;
import com.haydeproductions.project.log.NoOpLogHandler;
import com.haydeproductions.project.quarantine.QuarantineService;
import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.RuleResult;
import com.haydeproductions.project.rule.action.DeleteAction;
import com.haydeproductions.project.rule.action.FlagAction;
import com.haydeproductions.project.rule.action.QuarantineAction;
import com.haydeproductions.project.rule.condition.content.FileSignatureCondition;
import com.haydeproductions.project.rule.condition.content.HashCondition;
import com.haydeproductions.project.rule.condition.content.MimeTypeCondition;
import com.haydeproductions.project.rule.condition.logic.AndCondition;
import com.haydeproductions.project.rule.condition.logic.NotCondition;
import com.haydeproductions.project.rule.condition.logic.OrCondition;
import com.haydeproductions.project.rule.condition.metadata.ModifiedTimeCondition;
import com.haydeproductions.project.rule.condition.metadata.SizeCondition;
import com.haydeproductions.project.rule.condition.path.ExtensionCondition;
import com.haydeproductions.project.rule.condition.path.FileNameCondition;
import com.haydeproductions.project.rule.condition.path.PathCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigRuleSyntaxTest {

    @TempDir
    Path tempDir;

    @Test
    void versionOneIsAccepted() throws Exception {
        Config config = load("""
                version: 1
                ruleSets: []
                """);

        assertTrue(config.getRuleSets().isEmpty());
    }

    @Test
    void omittedVersionDefaultsToCurrentVersion() throws Exception {
        Config config = load("ruleSets: []\n");
        assertTrue(config.getRuleSets().isEmpty());
    }

    @Test
    void unsupportedVersionIsRejected() {
        assertThrows(
                ConfigException.class,
                () -> load("""
                        version: 2
                        ruleSets: []
                        """)
        );
    }

    @Test
    void ruleEntryMustContainExactlyOneKind() {
        assertThrows(
                ConfigException.class,
                () -> load("""
                        ruleSets:
                          - path: "."
                            rules:
                              - custom:
                                  condition: true
                                predefined:
                                  type: blockExtensions
                                  extensions: [exe]
                        """)
        );
    }

    @Test
    void unknownRuleKindIsRejected() {
        assertThrows(
                ConfigException.class,
                () -> load("""
                        ruleSets:
                          - rules:
                              - magic:
                                  condition: true
                        """)
        );
    }

    @Test
    void customTrueConditionCompiles() throws Exception {
        Rule rule = firstRule("""
                - custom:
                    condition: true
                """);

        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("anything.txt")))
        );
    }

    @Test
    void customFalseConditionCompiles() throws Exception {
        Rule rule = firstRule("""
                - custom:
                    condition: false
                """);

        assertEquals(
                RuleResult.NO_MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("anything.txt")))
        );
    }

    @Test
    void customLogicalTreeCompilesToBackendConditions() throws Exception {
        Rule rule = firstRule("""
                - custom:
                    condition:
                      and:
                        - extension:
                            values: [txt]
                        - or:
                            - fileName:
                                operator: startsWith
                                pattern: "safe"
                            - not: false
                """);

        assertInstanceOf(AndCondition.class, rule.getCondition());
        AndCondition and = (AndCondition) rule.getCondition();
        assertInstanceOf(ExtensionCondition.class, and.getConditions().get(0));
        assertInstanceOf(OrCondition.class, and.getConditions().get(1));

        Path file = tempDir.resolve("safe-file.txt");
        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void customRuleCompilesBothActionBranches() throws Exception {
        Rule rule = firstRule("""
                - custom:
                    condition: true
                    onMatch: [flag]
                    onNoMatch: flag
                """);

        assertEquals(1, rule.getOnMatch().size());
        assertInstanceOf(FlagAction.class, rule.getOnMatch().get(0));
        assertEquals(1, rule.getOnNoMatch().size());
        assertInstanceOf(FlagAction.class, rule.getOnNoMatch().get(0));
    }

    @Test
    void customRuleBranchesDefaultToEmpty() throws Exception {
        Rule rule = firstRule("""
                - custom:
                    condition: true
                """);

        assertTrue(rule.getOnMatch().isEmpty());
        assertTrue(rule.getOnNoMatch().isEmpty());
    }

    @Test
    void customExtensionConditionEvaluates() throws Exception {
        Rule rule = firstRule("""
                - custom:
                    condition:
                      extension:
                        values: [EXE, dll]
                        operator: in
                        caseSensitive: false
                """);

        assertInstanceOf(ExtensionCondition.class, rule.getCondition());
        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("thing.exe")))
        );
    }

    @Test
    void customFileNameConditionEvaluates() throws Exception {
        Rule rule = firstRule("""
                - custom:
                    condition:
                      fileName:
                        operator: glob
                        pattern: "*.tmp"
                        caseSensitive: false
                """);

        assertInstanceOf(FileNameCondition.class, rule.getCondition());
        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("THING.TMP")))
        );
    }

    @Test
    void customPathConditionUsesGlobalSourceRoot() throws Exception {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - path: "uploads"
                    rules:
                      - custom:
                          condition:
                            path:
                              operator: glob
                              pattern: "uploads/**/*.exe"
                """);

        Config config = ConfigLoader.load(yaml, root);
        Rule rule = config.getRuleSets().get(0).getRules().get(0);

        assertInstanceOf(PathCondition.class, rule.getCondition());
        Path file = root.resolve("uploads/a/bad.exe");
        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void customSizeSupportsHumanReadableUnits() throws Exception {
        Path file = tempDir.resolve("size.bin");
        Files.write(file, new byte[1536]);

        Rule rule = firstRule("""
                - custom:
                    condition:
                      size:
                        operator: equal
                        value: 1.5KiB
                """);

        assertInstanceOf(SizeCondition.class, rule.getCondition());
        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void customSizeSupportsInclusiveRange() throws Exception {
        Path file = tempDir.resolve("size.bin");
        Files.write(file, new byte[1500]);

        Rule rule = firstRule("""
                - custom:
                    condition:
                      size:
                        betweenInclusive: [1KB, 2KB]
                """);

        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void customModifiedTimeSupportsRelativeDuration() throws Exception {
        Path file = tempDir.resolve("old.txt");
        Files.writeString(file, "test");
        Files.setLastModifiedTime(
                file,
                FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS))
        );

        Rule rule = firstRule("""
                - custom:
                    condition:
                      modifiedTime:
                        olderThan: 2d
                """);

        assertInstanceOf(ModifiedTimeCondition.class, rule.getCondition());
        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void customModifiedTimeSupportsAbsoluteInstant() throws Exception {
        Path file = tempDir.resolve("time.txt");
        Files.writeString(file, "test");
        Files.setLastModifiedTime(
                file,
                FileTime.from(Instant.parse("2026-01-02T00:00:00Z"))
        );

        Rule rule = firstRule("""
                - custom:
                    condition:
                      modifiedTime:
                        after: "2026-01-01T00:00:00Z"
                """);

        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void customHashConditionEvaluates() throws Exception {
        Path file = tempDir.resolve("hash.txt");
        Files.writeString(file, "hash me");
        String hash = new FileFingerprintService()
                .fingerprint(file)
                .sha256();

        Rule rule = firstRule("""
                - custom:
                    condition:
                      hash:
                        values: ["%s"]
                        operator: in
                """.formatted(hash));

        assertInstanceOf(HashCondition.class, rule.getCondition());
        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void customFileSignatureConditionEvaluates() throws Exception {
        Path file = tempDir.resolve("renamed.jpg");
        Files.write(file, new byte[]{'M', 'Z', 0, 0});

        Rule rule = firstRule("""
                - custom:
                    condition:
                      fileSignature:
                        values: [pe]
                """);

        assertInstanceOf(FileSignatureCondition.class, rule.getCondition());
        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void customMimeTypeConditionEvaluatesUsingDetectedSignature() throws Exception {
        Path file = tempDir.resolve("renamed.txt");
        Files.write(file, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        });

        Rule rule = firstRule("""
                - custom:
                    condition:
                      mimeType:
                        patterns: ["image/*"]
                """);

        assertInstanceOf(MimeTypeCondition.class, rule.getCondition());
        assertEquals(RuleResult.MATCH, rule.evaluate(new FileContext(file)));
    }

    @Test
    void predefinedRuleDefaultsToFlagOnMatch() throws Exception {
        Rule rule = firstRule("""
                - predefined:
                    type: blockExtensions
                    extensions: [exe]
                """);

        assertEquals(1, rule.getOnMatch().size());
        assertInstanceOf(FlagAction.class, rule.getOnMatch().get(0));
        assertTrue(rule.getOnNoMatch().isEmpty());
    }

    @Test
    void predefinedBlockExtensionsMatchesListedExtension() throws Exception {
        Rule rule = firstRule("""
                - predefined:
                    type: blockExtensions
                    extensions: [exe, dll]
                """);

        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("bad.exe")))
        );
        assertEquals(
                RuleResult.NO_MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("good.txt")))
        );
    }

    @Test
    void predefinedAllowExtensionsMatchesViolation() throws Exception {
        Rule rule = firstRule("""
                - predefined:
                    type: allowExtensions
                    extensions: [jpg, png]
                """);

        assertEquals(
                RuleResult.NO_MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("good.jpg")))
        );
        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("bad.exe")))
        );
    }

    @Test
    void predefinedBlockFileNamesCombinesPatternsWithOr() throws Exception {
        Rule rule = firstRule("""
                - predefined:
                    type: blockFileNames
                    patterns: ["*.tmp", "desktop.ini"]
                    caseSensitive: false
                """);

        assertInstanceOf(OrCondition.class, rule.getCondition());
        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("CACHE.TMP")))
        );
        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(tempDir.resolve("DESKTOP.INI")))
        );
    }

    @Test
    void predefinedBlockPathsUsesRootRelativePaths() throws Exception {
        Path root = createRoot();
        Path yaml = writeYaml("""
                ruleSets:
                  - rules:
                      - predefined:
                          type: blockPaths
                          patterns: ["private/**"]
                """);

        Rule rule = ConfigLoader.load(yaml, root)
                .getRuleSets().get(0)
                .getRules().get(0);

        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(root.resolve("private/a.txt")))
        );
    }

    @Test
    void predefinedMaxAndMinFileSizeCompile() throws Exception {
        Config config = loadRules("""
                - predefined:
                    type: maxFileSize
                    max: 10MiB
                - predefined:
                    type: minFileSize
                    min: 1KiB
                """);

        assertEquals(2, config.getRuleSets().get(0).getRules().size());
        assertInstanceOf(
                SizeCondition.class,
                config.getRuleSets().get(0).getRules().get(0).getCondition()
        );
        assertInstanceOf(
                SizeCondition.class,
                config.getRuleSets().get(0).getRules().get(1).getCondition()
        );
    }

    @Test
    void allPredefinedTypesCompile() throws Exception {
        String hash = "0".repeat(64);

        Config config = loadRules("""
                - predefined:
                    type: blockExtensions
                    extensions: [exe]
                - predefined:
                    type: allowExtensions
                    extensions: [txt]
                - predefined:
                    type: blockFileNames
                    patterns: ["*.tmp"]
                - predefined:
                    type: blockPaths
                    patterns: ["private/**"]
                - predefined:
                    type: maxFileSize
                    max: 10MiB
                - predefined:
                    type: minFileSize
                    min: 1KiB
                - predefined:
                    type: blockHashes
                    hashes: ["%s"]
                - predefined:
                    type: allowHashes
                    hashes: ["%s"]
                - predefined:
                    type: blockMimeTypes
                    mimeTypes: ["image/*"]
                - predefined:
                    type: allowMimeTypes
                    mimeTypes: ["image/*"]
                - predefined:
                    type: blockSignatures
                    signatures: [PE]
                - predefined:
                    type: allowSignatures
                    signatures: [PNG, JPEG]
                - predefined:
                    type: olderThan
                    age: 30d
                - predefined:
                    type: newerThan
                    age: PT2H
                """.formatted(hash, hash));

        assertEquals(14, config.getRuleSets().get(0).getRules().size());
    }

    @Test
    void predefinedActionsCanBeOverridden() throws Exception {
        ConfigActionServices services = ConfigActionServices.of(
                NoOpLogHandler.INSTANCE,
                new QuarantineService(tempDir.resolve("quarantine"))
        );

        Rule rule = firstRule(
                """
                - predefined:
                    type: blockExtensions
                    extensions: [exe]
                    onMatch: [flag, quarantine, delete]
                    onNoMatch: flag
                """,
                services
        );

        assertEquals(3, rule.getOnMatch().size());
        assertInstanceOf(FlagAction.class, rule.getOnMatch().get(0));
        assertInstanceOf(QuarantineAction.class, rule.getOnMatch().get(1));
        assertInstanceOf(DeleteAction.class, rule.getOnMatch().get(2));
        assertInstanceOf(FlagAction.class, rule.getOnNoMatch().get(0));
    }

    @Test
    void deleteActionRequiresConfiguredLogHandler() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> firstRule("""
                        - custom:
                            condition: true
                            onMatch: delete
                        """)
        );

        assertTrue(exception.getMessage().contains("LogHandler"));
    }

    @Test
    void quarantineActionRequiresConfiguredQuarantineService() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> firstRule(
                        """
                        - custom:
                            condition: true
                            onMatch: quarantine
                        """,
                        ConfigActionServices.withLogHandler(
                                NoOpLogHandler.INSTANCE
                        )
                )
        );

        assertTrue(exception.getMessage().contains("QuarantineService"));
    }

    @Test
    void unknownCustomFieldIsRejected() {
        assertThrows(
                ConfigException.class,
                () -> firstRule("""
                        - custom:
                            condition: true
                            mystery: 123
                        """)
        );
    }

    @Test
    void unknownConditionFieldIsRejected() {
        assertThrows(
                ConfigException.class,
                () -> firstRule("""
                        - custom:
                            condition:
                              extension:
                                values: [exe]
                                mystery: true
                        """)
        );
    }

    @Test
    void unknownPredefinedFieldIsRejected() {
        assertThrows(
                ConfigException.class,
                () -> firstRule("""
                        - predefined:
                            type: blockExtensions
                            extensions: [exe]
                            mystery: true
                        """)
        );
    }

    @Test
    void unknownConditionTypeIsRejected() {
        assertThrows(
                ConfigException.class,
                () -> firstRule("""
                        - custom:
                            condition:
                              magicalCondition:
                                value: 1
                        """)
        );
    }

    @Test
    void invalidSizeSyntaxIsRejected() {
        assertThrows(
                ConfigException.class,
                () -> firstRule("""
                        - custom:
                            condition:
                              size:
                                value: 5MiB
                        """)
        );
    }

    @Test
    void invalidDurationSyntaxIsRejected() {
        assertThrows(
                ConfigException.class,
                () -> firstRule("""
                        - predefined:
                            type: olderThan
                            age: "later"
                        """)
        );
    }

    @Test
    void completeMixedConfigurationLoadsInDeclaredOrder() throws Exception {
        Config config = loadRules("""
                - predefined:
                    type: blockExtensions
                    extensions: [exe]
                - custom:
                    condition:
                      and:
                        - size:
                            operator: greaterThan
                            value: 1MiB
                        - not:
                            mimeType:
                              patterns: ["image/*"]
                    onMatch: flag
                - predefined:
                    type: blockSignatures
                    signatures: [PE, ELF]
                """);

        List<Rule> rules = config.getRuleSets().get(0).getRules();
        assertEquals(3, rules.size());
        assertInstanceOf(ExtensionCondition.class, rules.get(0).getCondition());
        assertInstanceOf(AndCondition.class, rules.get(1).getCondition());
        assertInstanceOf(FileSignatureCondition.class, rules.get(2).getCondition());
    }

    private Config load(String yaml) throws Exception {
        return ConfigLoader.load(writeYaml(yaml), createRoot());
    }

    private Config loadRules(String rulesYaml) throws Exception {
        return load("""
                version: 1
                ruleSets:
                  - path: "."
                    rules:
                %s
                """.formatted(indent(rulesYaml, 6)));
    }

    private Rule firstRule(String rulesYaml) throws Exception {
        return firstRule(rulesYaml, ConfigActionServices.none());
    }

    private Rule firstRule(
            String rulesYaml,
            ConfigActionServices services
    ) throws Exception {
        Path root = createRoot();
        Path yaml = writeYaml("""
                version: 1
                ruleSets:
                  - path: "."
                    rules:
                %s
                """.formatted(indent(rulesYaml, 6)));

        return ConfigLoader.load(yaml, root, services)
                .getRuleSets().get(0)
                .getRules().get(0);
    }

    private Path createRoot() throws IOException {
        Path root = tempDir.resolve("source-root");
        Files.createDirectories(root);
        return root;
    }

    private Path writeYaml(String yaml) throws IOException {
        Path path = tempDir.resolve("config-" + System.nanoTime() + ".yaml");
        Files.writeString(path, yaml);
        return path;
    }

    private String indent(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        return value.strip().lines()
                .map(line -> prefix + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
