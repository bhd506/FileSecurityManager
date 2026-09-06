package com.haydeproductions.project.scan;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.scope.RuleSet;

import java.nio.file.Path;
import java.util.Objects;

public final class ScanCoordinator {

    private final RuleSetIndex ruleSetIndex;

    public ScanCoordinator(RuleSetIndex ruleSetIndex) {
        this.ruleSetIndex = Objects.requireNonNull(ruleSetIndex);
    }

    public ScanSession scan(Path file)
            throws ConditionEvaluationException {

        Objects.requireNonNull(file);

        ScanSession session =
                new ScanSession(new FileContext(file));

        for (RuleSet ruleSet
                : ruleSetIndex.findCandidates(file)) {

            ruleSet.applyRules(session);
        }

        return session;
    }
}