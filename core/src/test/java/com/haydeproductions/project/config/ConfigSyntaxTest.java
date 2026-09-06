package com.haydeproductions.project.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigSyntaxTest {

    @Test
    void currentVersionIsOne() {
        assertEquals(1, ConfigSyntax.CURRENT_VERSION);
    }

    @Test
    void exposesSupportedActions() {
        assertEquals(
                java.util.Set.of("flag", "quarantine", "delete"),
                ConfigSyntax.ACTIONS
        );
    }

    @Test
    void exposesCoreCustomConditionTypes() {
        assertTrue(ConfigSyntax.CUSTOM_CONDITIONS.contains("and"));
        assertTrue(ConfigSyntax.CUSTOM_CONDITIONS.contains("extension"));
        assertTrue(ConfigSyntax.CUSTOM_CONDITIONS.contains("fileSignature"));
    }

    @Test
    void exposesPredefinedRuleTypes() {
        assertTrue(ConfigSyntax.PREDEFINED_RULES.contains("blockExtensions"));
        assertTrue(ConfigSyntax.PREDEFINED_RULES.contains("maxFileSize"));
        assertTrue(ConfigSyntax.PREDEFINED_RULES.contains("allowSignatures"));
    }

    @Test
    void exposedSyntaxSetsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> ConfigSyntax.ACTIONS.add("something")
        );
    }
}
