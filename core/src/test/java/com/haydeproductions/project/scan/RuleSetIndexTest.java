package com.haydeproductions.project.scan;

import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuleSetIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyIndexReturnsNoCandidates() {
        RuleSetIndex index =
                new RuleSetIndex(List.of());

        assertTrue(
                index.findCandidates(
                        tempDir.resolve("file.txt")
                ).isEmpty()
        );
    }

    @Test
    void findsRuleSetRootedAtFilesParent() {
        Path root = tempDir.resolve("data");

        RuleSet ruleSet = ruleSet(root);

        RuleSetIndex index =
                new RuleSetIndex(List.of(ruleSet));

        assertEquals(
                List.of(ruleSet),
                index.findCandidates(
                        root.resolve("file.txt")
                )
        );
    }

    @Test
    void findsAllRuleSetsWhoseRootsAreAncestors() {
        Path root = tempDir.resolve("data");
        Path privateRoot = root.resolve("private");
        Path uploadsRoot = privateRoot.resolve("uploads");

        RuleSet outer = ruleSet(root);
        RuleSet middle = ruleSet(privateRoot);
        RuleSet inner = ruleSet(uploadsRoot);

        RuleSetIndex index =
                new RuleSetIndex(
                        List.of(
                                outer,
                                middle,
                                inner
                        )
                );

        assertEquals(
                List.of(
                        outer,
                        middle,
                        inner
                ),
                index.findCandidates(
                        uploadsRoot.resolve("file.txt")
                )
        );
    }

    @Test
    void unrelatedRuleSetIsNotReturned() {
        Path dataRoot = tempDir.resolve("data");
        Path otherRoot = tempDir.resolve("other");

        RuleSet relevant = ruleSet(dataRoot);
        RuleSet unrelated = ruleSet(otherRoot);

        RuleSetIndex index =
                new RuleSetIndex(
                        List.of(
                                relevant,
                                unrelated
                        )
                );

        assertEquals(
                List.of(relevant),
                index.findCandidates(
                        dataRoot.resolve("file.txt")
                )
        );
    }

    @Test
    void descendantRuleSetIsNotReturnedForParentFile() {
        Path root = tempDir.resolve("data");
        Path nestedRoot = root.resolve("nested");

        RuleSet parent = ruleSet(root);
        RuleSet descendant = ruleSet(nestedRoot);

        RuleSetIndex index =
                new RuleSetIndex(
                        List.of(
                                parent,
                                descendant
                        )
                );

        assertEquals(
                List.of(parent),
                index.findCandidates(
                        root.resolve("file.txt")
                )
        );
    }

    @Test
    void siblingRuleSetIsNotReturned() {
        Path root = tempDir.resolve("data");

        RuleSet left =
                ruleSet(root.resolve("left"));

        RuleSet right =
                ruleSet(root.resolve("right"));

        RuleSetIndex index =
                new RuleSetIndex(
                        List.of(left, right)
                );

        assertEquals(
                List.of(left),
                index.findCandidates(
                        root.resolve("left/file.txt")
                )
        );
    }

    @Test
    void similarlyNamedSiblingIsNotReturned() {
        Path data = tempDir.resolve("data");

        RuleSet dataRuleSet =
                ruleSet(data);

        RuleSet dataOtherRuleSet =
                ruleSet(tempDir.resolve("data-other"));

        RuleSetIndex index =
                new RuleSetIndex(
                        List.of(
                                dataRuleSet,
                                dataOtherRuleSet
                        )
                );

        assertEquals(
                List.of(dataRuleSet),
                index.findCandidates(
                        data.resolve("file.txt")
                )
        );
    }

    @Test
    void normalizesFilePathBeforeLookup() {
        Path root = tempDir.resolve("data");

        RuleSet ruleSet = ruleSet(root);

        RuleSetIndex index =
                new RuleSetIndex(List.of(ruleSet));

        Path file = root
                .resolve("folder")
                .resolve("..")
                .resolve("file.txt");

        assertEquals(
                List.of(ruleSet),
                index.findCandidates(file)
        );
    }

    @Test
    void preservesConfigurationOrderRatherThanPathDiscoveryOrder() {
        Path root = tempDir.resolve("data");
        Path childRoot = root.resolve("child");

        RuleSet child = ruleSet(childRoot);
        RuleSet parent = ruleSet(root);

        /*
         * Deliberately configure child before parent.
         *
         * Ancestor lookup discovers child first anyway,
         * but this test establishes that configuration
         * order is the contract rather than tree depth.
         */
        RuleSetIndex index =
                new RuleSetIndex(
                        List.of(
                                child,
                                parent
                        )
                );

        assertEquals(
                List.of(
                        child,
                        parent
                ),
                index.findCandidates(
                        childRoot.resolve("file.txt")
                )
        );
    }

    @Test
    void preservesConfigurationOrderWhenParentConfiguredBeforeChild() {
        Path root = tempDir.resolve("data");
        Path childRoot = root.resolve("child");

        RuleSet parent = ruleSet(root);
        RuleSet child = ruleSet(childRoot);

        RuleSetIndex index =
                new RuleSetIndex(
                        List.of(
                                parent,
                                child
                        )
                );

        assertEquals(
                List.of(
                        parent,
                        child
                ),
                index.findCandidates(
                        childRoot.resolve("file.txt")
                )
        );
    }

    @Test
    void multipleRuleSetsAtSameRootAreReturnedInConfigurationOrder() {
        Path root = tempDir.resolve("data");

        RuleSet first = ruleSet(root);
        RuleSet second = ruleSet(root);
        RuleSet third = ruleSet(root);

        RuleSetIndex index =
                new RuleSetIndex(
                        List.of(
                                first,
                                second,
                                third
                        )
                );

        assertEquals(
                List.of(
                        first,
                        second,
                        third
                ),
                index.findCandidates(
                        root.resolve("file.txt")
                )
        );
    }

    @Test
    void maxDepthDoesNotAffectCandidateLookup() {
        Path root = tempDir.resolve("data");

        RuleSet shallow = RuleSet.builder(
                        root,
                        emptyOverrides()
                )
                .maxDepth(1)
                .build();

        RuleSetIndex index =
                new RuleSetIndex(List.of(shallow));

        /*
         * This file is beyond the RuleSet's maxDepth,
         * but the index should still return the RuleSet.
         *
         * isFileOwned() performs the exact depth check.
         */
        assertEquals(
                List.of(shallow),
                index.findCandidates(
                        root.resolve(
                                "one/two/three/file.txt"
                        )
                )
        );

        assertFalse(
                shallow.isFileOwned(
                        root.resolve(
                                "one/two/three/file.txt"
                        )
                )
        );
    }

    @Test
    void overridesDoNotAffectCandidateLookup() {
        Path root = tempDir.resolve("data");
        Path overrideRoot = root.resolve("private");

        OverrideRegistry registry =
                new OverrideRegistry(
                        Set.of(overrideRoot)
                );

        RuleSet parent =
                RuleSet.builder(root, registry)
                        .build();

        RuleSetIndex index =
                new RuleSetIndex(List.of(parent));

        Path file =
                overrideRoot.resolve("file.txt");

        /*
         * Root ancestry makes it a candidate.
         * Exact override rejection belongs to RuleSet.
         */
        assertEquals(
                List.of(parent),
                index.findCandidates(file)
        );

        assertFalse(
                parent.isFileOwned(file)
        );
    }

    @Test
    void candidateListIsImmutable() {
        Path root = tempDir.resolve("data");

        RuleSet ruleSet = ruleSet(root);

        RuleSetIndex index =
                new RuleSetIndex(List.of(ruleSet));

        List<RuleSet> candidates =
                index.findCandidates(
                        root.resolve("file.txt")
                );

        assertThrows(
                UnsupportedOperationException.class,
                () -> candidates.add(ruleSet)
        );
    }

    @Test
    void constructionDoesNotDependOnOriginalListAfterwards() {
        Path firstRoot = tempDir.resolve("first");
        Path secondRoot = tempDir.resolve("second");

        RuleSet first = ruleSet(firstRoot);
        RuleSet second = ruleSet(secondRoot);

        List<RuleSet> source =
                new ArrayList<>();

        source.add(first);

        RuleSetIndex index =
                new RuleSetIndex(source);

        source.add(second);

        assertEquals(
                List.of(first),
                index.findCandidates(
                        firstRoot.resolve("file.txt")
                )
        );

        assertTrue(
                index.findCandidates(
                        secondRoot.resolve("file.txt")
                ).isEmpty()
        );
    }

    @Test
    void constructorRejectsNullRuleSetList() {
        assertThrows(
                NullPointerException.class,
                () -> new RuleSetIndex(null)
        );
    }

    @Test
    void constructorRejectsNullRuleSetElement() {
        List<RuleSet> ruleSets =
                new ArrayList<>();

        ruleSets.add(
                ruleSet(tempDir.resolve("data"))
        );

        ruleSets.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new RuleSetIndex(ruleSets)
        );
    }

    @Test
    void findCandidatesRejectsNullFile() {
        RuleSetIndex index =
                new RuleSetIndex(List.of());

        assertThrows(
                NullPointerException.class,
                () -> index.findCandidates(null)
        );
    }

    private RuleSet ruleSet(Path root) {
        return RuleSet.builder(
                root,
                emptyOverrides()
        ).build();
    }

    private OverrideRegistry emptyOverrides() {
        return new OverrideRegistry(Set.of());
    }
}