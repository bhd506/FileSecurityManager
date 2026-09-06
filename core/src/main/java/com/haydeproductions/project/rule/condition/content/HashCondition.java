package com.haydeproductions.project.rule.condition.content;

import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class HashCondition implements Condition {

    private final Set<String> sha256Hashes;
    private final MembershipOperator operator;

    public HashCondition(String sha256Hash) {
        this(Set.of(sha256Hash), MembershipOperator.IN);
    }

    public HashCondition(Collection<String> sha256Hashes) {
        this(sha256Hashes, MembershipOperator.IN);
    }

    public HashCondition(
            Collection<String> sha256Hashes,
            MembershipOperator operator
    ) {
        Objects.requireNonNull(sha256Hashes);
        this.operator = Objects.requireNonNull(operator);

        if (sha256Hashes.isEmpty()) {
            throw new IllegalArgumentException(
                    "HashCondition requires at least one SHA-256 hash"
            );
        }

        this.sha256Hashes = sha256Hashes.stream()
                .map(Objects::requireNonNull)
                .map(HashCondition::normalizeHash)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean matches(FileContext file)
            throws ConditionEvaluationException {
        Objects.requireNonNull(file);

        try {
            boolean contained = sha256Hashes.contains(
                    file.getSha256().toLowerCase(Locale.ROOT)
            );

            return operator.apply(contained);
        } catch (IOException exception) {
            throw new ConditionEvaluationException(
                    "Failed to calculate SHA-256: "
                            + file.getNormalizedPath(),
                    exception
            );
        }
    }

    public Set<String> getSha256Hashes() {
        return sha256Hashes;
    }

    private static String normalizeHash(String hash) {
        String normalized = hash.trim().toLowerCase(Locale.ROOT);

        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Invalid SHA-256 hash: " + hash
            );
        }

        return normalized;
    }
}
