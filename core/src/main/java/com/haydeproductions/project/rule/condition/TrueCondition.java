package com.haydeproductions.project.rule.condition;

import com.haydeproductions.project.rule.FileContext;

public final class TrueCondition implements Condition {

    @Override
    public boolean matches(FileContext file) {
        return true;
    }
}