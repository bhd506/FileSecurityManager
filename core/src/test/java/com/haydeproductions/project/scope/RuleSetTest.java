package com.haydeproductions.project.scope;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.action.Action;
import com.haydeproductions.project.rule.action.NoOpAction;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.FalseCondition;
import com.haydeproductions.project.rule.condition.TrueCondition;
import com.haydeproductions.project.scan.ScanSession;
import com.haydeproductions.project.scan.ScheduledAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

        assertEquals(
                Set.of(rootFile, nestedFile, deepFile),
                ruleSet.visited()
        );
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

        assertEquals(
                Set.of(direct),
                ruleSet.visited()
        );
    }

    @Test
    void maxDepthTwoProcessesDirectFilesAndOneSubdirectoryLevel() throws IOException {
        Path direct = createFile("direct.txt");
        Path nested = createFile("folder/nested.txt");
        createFile("folder/deeper/deep.txt");

        RecordingRuleSet ruleSet = ruleSet(root, 2);
        ruleSet.applyRules();

        assertEquals(
                Set.of(direct, nested),
                ruleSet.visited()
        );
    }

    @Test
    void directoriesAreNotPassedToApplyRule() throws IOException {
        Path file = createFile("file.txt");

        Files.createDirectories(
                root.resolve("empty-directory")
        );

        Files.createDirectories(
                root.resolve("nested/another-empty-directory")
        );

        RecordingRuleSet ruleSet = ruleSet(root);
        ruleSet.applyRules();

        assertEquals(
                Set.of(file),
                ruleSet.visited()
        );
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

        assertEquals(
                Set.of(rootFile, publicFile),
                ruleSet.visited()
        );
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

        assertEquals(
                Set.of(visible),
                ruleSet.visited()
        );
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

        assertEquals(
                Set.of(direct, nested),
                ruleSet.visited()
        );
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

        assertEquals(
                Set.of(visible),
                ruleSet.visited()
        );
    }

    @Test
    void overrideOutsideCurrentScopeHasNoEffect() throws IOException {
        Path file = createFile("file.txt");

        RecordingRuleSet ruleSet = new RecordingRuleSet(
                root,
                Integer.MAX_VALUE,
                new OverrideRegistry(
                        Set.of(
                                root.getParent()
                                        .resolve("elsewhere")
                        )
                )
        );

        ruleSet.applyRules();

        assertEquals(
                Set.of(file),
                ruleSet.visited()
        );
    }

    @Test
    void maxDepthStillAppliesWhenOverrideRegistryIsPresent() throws IOException {
        Path direct = createFile("direct.txt");

        createFile("normal/nested.txt");
        createFile("private/hidden.txt");

        RecordingRuleSet ruleSet = new RecordingRuleSet(
                root,
                1,
                new OverrideRegistry(
                        Set.of(root.resolve("private"))
                )
        );

        ruleSet.applyRules();

        assertEquals(
                Set.of(direct),
                ruleSet.visited()
        );
    }

    @Test
    void nonexistentRootThrowsNoSuchFileException() {
        RecordingRuleSet ruleSet = ruleSet(
                root.resolve("missing")
        );

        assertThrows(
                NoSuchFileException.class,
                ruleSet::applyRules
        );
    }

    @Test
    void negativeMaxDepthIsRejectedAtConfigurationTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RuleSet.builder(
                                root,
                                new OverrideRegistry(Set.of())
                        )
                        .maxDepth(-1)
        );
    }

    // -------------------------------------------------------------------------
    // isFileOwned
    // -------------------------------------------------------------------------

    @Test
    void ownsDirectChild() {
        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).build();

        Path file = root.resolve("file.txt");

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void ownsFileWithinMaxDepth() {
        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .maxDepth(3)
                .build();

        Path file = root
                .resolve("one")
                .resolve("two")
                .resolve("file.txt");

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void ownsFileExactlyAtMaxDepth() {
        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .maxDepth(2)
                .build();

        Path file = root
                .resolve("one")
                .resolve("file.txt");

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void doesNotOwnFileBeyondMaxDepth() {
        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .maxDepth(2)
                .build();

        Path file = root
                .resolve("one")
                .resolve("two")
                .resolve("file.txt");

        assertFalse(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void maxDepthZeroOwnsNoFilesBelowRoot() {
        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .maxDepth(0)
                .build();

        assertFalse(
                ruleSet.isFileOwned(
                        root.resolve("file.txt")
                )
        );
    }

    @Test
    void doesNotOwnFileOutsideRoot() {
        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).build();

        Path outside = root
                .getParent()
                .resolve("outside")
                .resolve("file.txt");

        assertFalse(
                ruleSet.isFileOwned(outside)
        );
    }

    @Test
    void siblingWithSimilarNameIsNotOwned() {
        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).build();

        Path sibling = root
                .getParent()
                .resolve(root.getFileName() + "-other")
                .resolve("file.txt");

        assertFalse(
                ruleSet.isFileOwned(sibling)
        );
    }

    @Test
    void nestedOverrideBlocksOwnership() {
        Path overrideRoot = root.resolve("private");

        OverrideRegistry registry = new OverrideRegistry(
                Set.of(overrideRoot)
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                registry
        ).build();

        Path file = overrideRoot.resolve("file.txt");

        assertFalse(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void deeplyNestedOverrideBlocksOwnership() {
        Path overrideRoot = root
                .resolve("one")
                .resolve("two");

        OverrideRegistry registry = new OverrideRegistry(
                Set.of(overrideRoot)
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                registry
        ).build();

        Path file = overrideRoot
                .resolve("three")
                .resolve("file.txt");

        assertFalse(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void ownRootBeingOverrideDoesNotBlockOwnership() {
        OverrideRegistry registry = new OverrideRegistry(
                Set.of(root)
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                registry
        ).build();

        Path file = root.resolve("file.txt");

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void lowerOverrideStillBlocksOwnershipWhenOwnRootIsOverride() {
        Path lowerOverride = root.resolve("private");

        OverrideRegistry registry = new OverrideRegistry(
                Set.of(
                        root,
                        lowerOverride
                )
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                registry
        ).build();

        assertFalse(
                ruleSet.isFileOwned(
                        lowerOverride.resolve("file.txt")
                )
        );
    }

    @Test
    void ruleSetBelowParentOverrideStillOwnsItsFiles() {
        Path overrideRoot = root.resolve("private");
        Path childRoot = overrideRoot.resolve("uploads");

        OverrideRegistry registry = new OverrideRegistry(
                Set.of(overrideRoot)
        );

        RuleSet ruleSet = RuleSet.builder(
                childRoot,
                registry
        ).build();

        Path file = childRoot.resolve("file.txt");

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void unrelatedOverrideDoesNotBlockOwnership() {
        Path unrelatedOverride = root.resolve("private");

        OverrideRegistry registry = new OverrideRegistry(
                Set.of(unrelatedOverride)
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                registry
        ).build();

        Path file = root
                .resolve("public")
                .resolve("file.txt");

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void overrideOutsideRuleSetRootDoesNotBlockOwnership() {
        Path outsideOverride = root
                .getParent()
                .resolve("elsewhere");

        OverrideRegistry registry = new OverrideRegistry(
                Set.of(outsideOverride)
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                registry
        ).build();

        assertTrue(
                ruleSet.isFileOwned(
                        root.resolve("file.txt")
                )
        );
    }

    @Test
    void normalizesFilePathBeforeCheckingOwnership() {
        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).build();

        Path file = root
                .resolve("folder")
                .resolve("..")
                .resolve("file.txt");

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void normalizedPathCannotEscapeRuleSetRoot() {
        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).build();

        Path file = root
                .resolve("..")
                .resolve("outside")
                .resolve("file.txt");

        assertFalse(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void normalizedDepthIsUsedForOwnership() {
        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .maxDepth(2)
                .build();

        Path file = root
                .resolve("one")
                .resolve("two")
                .resolve("..")
                .resolve("file.txt");

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void ruleSetBelowParentOverrideIsStillBlockedByLowerOverride() {
        Path parentOverride = root.resolve("private");
        Path ruleSetRoot = parentOverride.resolve("uploads");
        Path lowerOverride = ruleSetRoot.resolve("restricted");

        OverrideRegistry registry = new OverrideRegistry(
                Set.of(
                        parentOverride,
                        lowerOverride
                )
        );

        RuleSet ruleSet = RuleSet.builder(
                ruleSetRoot,
                registry
        ).build();

        Path file = lowerOverride.resolve("file.txt");

        assertFalse(
                ruleSet.isFileOwned(file)
        );
    }

    @Test
    void overrideBlocksOnlyItsOwnBranch() {
        Path privateRoot = root.resolve("private");

        OverrideRegistry registry = new OverrideRegistry(
                Set.of(privateRoot)
        );

        RuleSet ruleSet = RuleSet.builder(
                root,
                registry
        ).build();

        assertFalse(
                ruleSet.isFileOwned(
                        privateRoot.resolve("file.txt")
                )
        );

        assertTrue(
                ruleSet.isFileOwned(
                        root.resolve("public/file.txt")
                )
        );
    }

    @Test
    void defaultMaxDepthOwnsDeeplyNestedFile() {
        RuleSet ruleSet = RuleSet.builder(
                root,
                new OverrideRegistry(Set.of())
        ).build();

        Path file = root.resolve(
                "one/two/three/four/five/six/file.txt"
        );

        assertTrue(
                ruleSet.isFileOwned(file)
        );
    }


    // -------------------------------------------------------------------------
    // Construction invariants
    // -------------------------------------------------------------------------

    @Test
    void ruleSetNormalizesRootBeforeOwnershipChecks() {
        Path nonNormalizedRoot = root
                .resolve("unused")
                .resolve("..");

        RuleSet ruleSet = RuleSet.builder(
                nonNormalizedRoot,
                new OverrideRegistry(Set.of())
        ).build();

        assertTrue(
                ruleSet.isFileOwned(
                        root.resolve("file.txt")
                )
        );
    }

    @Test
    void ruleSetNormalizesRootBeforeTraversal() throws IOException {
        Path file = createFile("file.txt");

        Path nonNormalizedRoot = root
                .resolve("does-not-exist")
                .resolve("..");

        RecordingRuleSet ruleSet = new RecordingRuleSet(
                nonNormalizedRoot,
                Integer.MAX_VALUE,
                new OverrideRegistry(Set.of())
        );

        ruleSet.applyRules();

        assertEquals(
                Set.of(file),
                ruleSet.visited()
        );
    }

    @Test
    void ruleSetRejectsNullRootWhenBuilt() {
        assertThrows(
                NullPointerException.class,
                () -> RuleSet.builder(
                        null,
                        new OverrideRegistry(Set.of())
                ).build()
        );
    }

    @Test
    void ruleSetRejectsNullOverrideRegistryWhenBuilt() {
        assertThrows(
                NullPointerException.class,
                () -> RuleSet.builder(
                        root,
                        null
                ).build()
        );
    }

    // -------------------------------------------------------------------------
    // Rule storage
    // -------------------------------------------------------------------------

    @Test
    void builderStoresRulesInOrder() {
        Rule first = new Rule(
                new TrueCondition(),
                List.of(),
                List.of()
        );

        Rule second = new Rule(
                new FalseCondition(),
                List.of(),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .rule(first)
                .rule(second)
                .build();

        assertEquals(
                List.of(first, second),
                ruleSet.getRules()
        );
    }

    @Test
    void ruleListIsImmutable() {
        Rule rule = new Rule(
                new TrueCondition(),
                List.of(),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .rule(rule)
                .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> ruleSet.getRules().add(
                        new Rule(
                                new TrueCondition(),
                                List.of(),
                                List.of()
                        )
                )
        );
    }

    @Test
    void ruleCollectionIsDefensivelyCopied() {
        List<Rule> source = new ArrayList<>();

        Rule first = new Rule(
                new TrueCondition(),
                List.of(),
                List.of()
        );

        Rule second = new Rule(
                new FalseCondition(),
                List.of(),
                List.of()
        );

        source.add(first);

        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .rules(source)
                .build();

        source.clear();
        source.add(second);

        assertEquals(
                List.of(first),
                ruleSet.getRules()
        );
    }

    // -------------------------------------------------------------------------
    // ScanSession integration
    // -------------------------------------------------------------------------

    @Test
    void ownedFileEvaluatesAllRulesIntoSharedSession() throws Exception {
        Action firstAction = new NoOpAction();
        Action secondAction = new NoOpAction();

        Rule firstRule = new Rule(
                new TrueCondition(),
                List.of(firstAction),
                List.of()
        );

        Rule secondRule = new Rule(
                new FalseCondition(),
                List.of(),
                List.of(secondAction)
        );

        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .rule(firstRule)
                .rule(secondRule)
                .build();

        ScanSession session = new ScanSession(
                new FileContext(root.resolve("file.txt"))
        );

        ruleSet.applyRules(session);

        assertEquals(
                List.of(
                        new ScheduledAction(firstRule, firstAction),
                        new ScheduledAction(secondRule, secondAction)
                ),
                session.getScheduledActions()
        );
    }

    @Test
    void unownedFileDoesNotEvaluateRules() throws Exception {
        AtomicBoolean evaluated = new AtomicBoolean(false);

        Rule rule = new Rule(
                file -> {
                    evaluated.set(true);
                    return true;
                },
                List.of(new NoOpAction()),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .rule(rule)
                .build();

        Path outside = root
                .getParent()
                .resolve("outside")
                .resolve("file.txt");

        ScanSession session = new ScanSession(
                new FileContext(outside)
        );

        ruleSet.applyRules(session);

        assertFalse(evaluated.get());
        assertTrue(session.getScheduledActions().isEmpty());
    }

    @Test
    void fileBelowOverrideDoesNotEvaluateRules() throws Exception {
        Path override = root.resolve("private");
        AtomicBoolean evaluated = new AtomicBoolean(false);

        Rule rule = new Rule(
                file -> {
                    evaluated.set(true);
                    return true;
                },
                List.of(new NoOpAction()),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of(override))
                )
                .rule(rule)
                .build();

        ScanSession session = new ScanSession(
                new FileContext(
                        override.resolve("file.txt")
                )
        );

        ruleSet.applyRules(session);

        assertFalse(evaluated.get());
        assertTrue(session.getScheduledActions().isEmpty());
    }

    @Test
    void multipleRulesScheduleActionsInRuleOrder() throws Exception {
        Action first = new NoOpAction();
        Action second = new NoOpAction();
        Action third = new NoOpAction();

        Rule firstRule = new Rule(
                new TrueCondition(),
                List.of(first, second),
                List.of()
        );

        Rule secondRule = new Rule(
                new FalseCondition(),
                List.of(),
                List.of(third)
        );

        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .rule(firstRule)
                .rule(secondRule)
                .build();

        ScanSession session = new ScanSession(
                new FileContext(root.resolve("file.txt"))
        );

        ruleSet.applyRules(session);

        assertEquals(
                List.of(
                        new ScheduledAction(firstRule, first),
                        new ScheduledAction(firstRule, second),
                        new ScheduledAction(secondRule, third)
                ),
                session.getScheduledActions()
        );
    }

    @Test
    void conditionFailurePropagatesFromRuleSet() {
        Rule rule = new Rule(
                file -> {
                    throw new ConditionEvaluationException(
                            "test failure"
                    );
                },
                List.of(),
                List.of()
        );

        RuleSet ruleSet = RuleSet.builder(
                        root,
                        new OverrideRegistry(Set.of())
                )
                .rule(rule)
                .build();

        ScanSession session = new ScanSession(
                new FileContext(root.resolve("file.txt"))
        );

        assertThrows(
                ConditionEvaluationException.class,
                () -> ruleSet.applyRules(session)
        );
    }

    @Test
    void multipleRuleSetsCanContributeToSameScanSession() throws Exception {
        Action firstAction = new NoOpAction();
        Action secondAction = new NoOpAction();

        Rule firstRule = new Rule(
                new TrueCondition(),
                List.of(firstAction),
                List.of()
        );

        Rule secondRule = new Rule(
                new TrueCondition(),
                List.of(secondAction),
                List.of()
        );

        OverrideRegistry registry =
                new OverrideRegistry(Set.of());

        RuleSet firstRuleSet = RuleSet.builder(
                        root,
                        registry
                )
                .rule(firstRule)
                .build();

        RuleSet secondRuleSet = RuleSet.builder(
                        root,
                        registry
                )
                .rule(secondRule)
                .build();

        ScanSession session = new ScanSession(
                new FileContext(root.resolve("file.txt"))
        );

        firstRuleSet.applyRules(session);
        secondRuleSet.applyRules(session);

        assertEquals(
                List.of(
                        new ScheduledAction(firstRule, firstAction),
                        new ScheduledAction(secondRule, secondAction)
                ),
                session.getScheduledActions()
        );
    }

    private RecordingRuleSet ruleSet(Path path) {
        return new RecordingRuleSet(
                path,
                Integer.MAX_VALUE,
                new OverrideRegistry(Set.of())
        );
    }

    private RecordingRuleSet ruleSet(
            Path path,
            int maxDepth
    ) {
        return new RecordingRuleSet(
                path,
                maxDepth,
                new OverrideRegistry(Set.of())
        );
    }

    private Path createFile(String relativePath)
            throws IOException {

        Path file = root.resolve(relativePath);

        Files.createDirectories(file.getParent());
        Files.writeString(file, "test");

        return file;
    }

    private static final class RecordingRuleSet
            extends RuleSet {

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