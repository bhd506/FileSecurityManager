package com.haydeproductions.project.rule.condition;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.Rule;
import com.haydeproductions.project.rule.RuleResult;
import com.haydeproductions.project.rule.action.FlagAction;
import com.haydeproductions.project.rule.action.NoOpAction;
import com.haydeproductions.project.rule.condition.content.FileSignatureCondition;
import com.haydeproductions.project.rule.condition.logic.AndCondition;
import com.haydeproductions.project.rule.condition.logic.NotCondition;
import com.haydeproductions.project.rule.condition.metadata.SizeCondition;
import com.haydeproductions.project.rule.condition.operator.LongComparisonOperator;
import com.haydeproductions.project.rule.condition.path.ExtensionCondition;
import com.haydeproductions.project.file.FileSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionRuleIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void complexConditionTreeWorksThroughRuleWithoutRuleKnowingConcreteTypes()
            throws Exception {

        Path file = tempDir.resolve("payload.jpg");
        Files.write(file, "MZ executable content".getBytes());

        Condition condition = new AndCondition(
                new ExtensionCondition("jpg"),
                new FileSignatureCondition(FileSignature.PE),
                new SizeCondition(
                        LongComparisonOperator.GREATER_THAN,
                        5
                ),
                new NotCondition(new FalseCondition())
        );

        Rule rule = new Rule(
                condition,
                List.of(new FlagAction()),
                List.of(new NoOpAction())
        );

        assertEquals(
                RuleResult.MATCH,
                rule.evaluate(new FileContext(file))
        );

        assertInstanceOf(
                FlagAction.class,
                rule.getActions(RuleResult.MATCH).get(0)
        );
    }
}
