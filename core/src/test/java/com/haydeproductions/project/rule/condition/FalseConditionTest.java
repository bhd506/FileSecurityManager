package com.haydeproductions.project.rule.condition;

import com.haydeproductions.project.rule.FileContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FalseConditionTest {

    @Test
    void neverMatches() throws ConditionEvaluationException {
        FileContext file = new FileContext(Path.of("anything.txt"));

        Condition condition = new FalseCondition();

        assertFalse(condition.matches(file));
    }
}