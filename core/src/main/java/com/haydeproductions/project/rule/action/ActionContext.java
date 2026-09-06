package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.rule.FileContext;

import java.util.Objects;

public final class ActionContext {

    private final FileContext file;

    public ActionContext(FileContext file) {
        this.file = Objects.requireNonNull(file);
    }

    public FileContext getFile() {
        return file;
    }
}