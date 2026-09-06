package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.rule.FileContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NoOpActionTest {

    @Test
    void executesWithoutChangingContractOrThrowing() {
        NoOpAction action = new NoOpAction();

        ActionContext context = new ActionContext(
                new FileContext(Path.of("file.txt"))
        );

        assertDoesNotThrow(
                () -> action.execute(context)
        );
    }
}
