package com.haydeproductions.project.scan;

import com.haydeproductions.project.scope.RuleSet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RuleSetIndex {

    private final Map<Path, List<IndexedRuleSet>> byRoot;

    public RuleSetIndex(List<RuleSet> ruleSets) {
        Objects.requireNonNull(ruleSets);

        Map<Path, List<IndexedRuleSet>> index = new HashMap<>();

        for (int i = 0; i < ruleSets.size(); i++) {
            RuleSet ruleSet = Objects.requireNonNull(ruleSets.get(i));

            index.computeIfAbsent(
                    ruleSet.getRoot(),
                    ignored -> new ArrayList<>()
            ).add(new IndexedRuleSet(i, ruleSet));
        }

        Map<Path, List<IndexedRuleSet>> immutableIndex =
                new HashMap<>();

        for (Map.Entry<Path, List<IndexedRuleSet>> entry
                : index.entrySet()) {

            immutableIndex.put(
                    entry.getKey(),
                    List.copyOf(entry.getValue())
            );
        }

        this.byRoot = Map.copyOf(immutableIndex);
    }

    public List<RuleSet> findCandidates(Path file) {
        Path normalizedFile = Objects.requireNonNull(file)
                .toAbsolutePath()
                .normalize();

        List<IndexedRuleSet> candidates =
                new ArrayList<>();

        Path current = normalizedFile.getParent();

        while (current != null) {
            List<IndexedRuleSet> ruleSets =
                    byRoot.get(current);

            if (ruleSets != null) {
                candidates.addAll(ruleSets);
            }

            current = current.getParent();
        }

        candidates.sort(
                (left, right) ->
                        Integer.compare(
                                left.order(),
                                right.order()
                        )
        );

        return candidates.stream()
                .map(IndexedRuleSet::ruleSet)
                .toList();
    }

    private record IndexedRuleSet(
            int order,
            RuleSet ruleSet
    ) {
    }
}