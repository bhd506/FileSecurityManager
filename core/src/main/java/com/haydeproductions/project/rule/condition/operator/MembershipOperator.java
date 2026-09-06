package com.haydeproductions.project.rule.condition.operator;

public enum MembershipOperator {
    IN,
    NOT_IN;

    public boolean apply(boolean contained) {
        return this == IN ? contained : !contained;
    }
}
