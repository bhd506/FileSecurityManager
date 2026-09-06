package com.haydeproductions.project.rule.condition;

import com.haydeproductions.project.rule.FileContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {

    @Test
    void customConditionCanImplementCommonContract() throws Exception {
        Condition condition = file ->
                file.getPath()
                        .getFileName()
                        .toString()
                        .equals("file.txt");

        assertTrue(
                condition.matches(
                        new FileContext(
                                Path.of("file.txt")
                        )
                )
        );
    }
}
