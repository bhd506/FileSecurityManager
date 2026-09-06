package com.haydeproductions.project.rule.condition.content;

import com.haydeproductions.project.file.FileSignature;
import com.haydeproductions.project.rule.FileContext;
import com.haydeproductions.project.rule.condition.Condition;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.rule.condition.operator.MembershipOperator;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public final class FileSignatureCondition implements Condition {

    private final Set<FileSignature> signatures;
    private final MembershipOperator operator;

    public FileSignatureCondition(FileSignature signature) {
        this(Set.of(signature), MembershipOperator.IN);
    }

    public FileSignatureCondition(Collection<FileSignature> signatures) {
        this(signatures, MembershipOperator.IN);
    }

    public FileSignatureCondition(
            Collection<FileSignature> signatures,
            MembershipOperator operator
    ) {
        Objects.requireNonNull(signatures);
        this.operator = Objects.requireNonNull(operator);

        if (signatures.isEmpty()) {
            throw new IllegalArgumentException(
                    "FileSignatureCondition requires at least one signature"
            );
        }

        this.signatures = Set.copyOf(signatures);
    }

    @Override
    public boolean matches(FileContext file)
            throws ConditionEvaluationException {
        Objects.requireNonNull(file);

        try {
            boolean contained = signatures.contains(
                    file.getFileSignature()
            );

            return operator.apply(contained);
        } catch (IOException exception) {
            throw new ConditionEvaluationException(
                    "Failed to inspect file signature: "
                            + file.getNormalizedPath(),
                    exception
            );
        }
    }
}
