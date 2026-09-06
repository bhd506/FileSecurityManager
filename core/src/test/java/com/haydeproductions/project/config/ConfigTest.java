package com.haydeproductions.project.config;

import com.haydeproductions.project.scope.OverrideRegistry;
import com.haydeproductions.project.scope.RuleSet;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void gettersReturnConfiguredValues() {
        Path root = Path.of("root");
        OverrideRegistry registry = new OverrideRegistry(Set.of());
        RuleSet ruleSet = RuleSet.builder(root, registry).build();

        Config config = new Config(root, registry, List.of(ruleSet));

        assertEquals(
                root.toAbsolutePath().normalize(),
                config.getRoot()
        );
        assertSame(registry, config.getOverrides());
        assertEquals(List.of(ruleSet), config.getRuleSets());
    }

    @Test
    void constructorDefensivelyCopiesRuleSetList() {
        Path root = Path.of("root");
        OverrideRegistry registry = new OverrideRegistry(Set.of());
        RuleSet first = RuleSet.builder(root, registry).build();
        RuleSet second = RuleSet.builder(root, registry).build();

        List<RuleSet> source = new ArrayList<>();
        source.add(first);

        Config config = new Config(root, registry, source);
        source.add(second);

        assertEquals(List.of(first), config.getRuleSets());
    }

    @Test
    void returnedRuleSetListIsUnmodifiable() {
        Path root = Path.of("root");
        OverrideRegistry registry = new OverrideRegistry(Set.of());
        RuleSet ruleSet = RuleSet.builder(root, registry).build();
        Config config = new Config(root, registry, List.of(ruleSet));

        assertThrows(
                UnsupportedOperationException.class,
                () -> config.getRuleSets().clear()
        );
    }

    @Test
    void preservesRuleSetOrdering() {
        Path root = Path.of("root");
        OverrideRegistry registry = new OverrideRegistry(Set.of());
        RuleSet first = RuleSet.builder(root.resolve("first"), registry).build();
        RuleSet second = RuleSet.builder(root.resolve("second"), registry).build();

        Config config = new Config(root, registry, List.of(first, second));

        assertSame(first, config.getRuleSets().get(0));
        assertSame(second, config.getRuleSets().get(1));
    }

    @Test
    void emptyRuleSetListIsSupported() {
        Path root = Path.of("root");
        OverrideRegistry registry = new OverrideRegistry(Set.of());

        Config config = new Config(root, registry, List.of());

        assertTrue(config.getRuleSets().isEmpty());
    }

    @Test
    void nullRuleSetListIsRejected() {
        Path root = Path.of("root");
        OverrideRegistry registry = new OverrideRegistry(Set.of());

        assertThrows(
                NullPointerException.class,
                () -> new Config(root, registry, null)
        );
    }
}
