package com.haydeproductions.project.rule.action;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class DenyMirrorAction implements Action {
    private final Set<String> mirrorIds;

    public DenyMirrorAction(Collection<String> mirrorIds) {
        Objects.requireNonNull(mirrorIds);
        if (mirrorIds.isEmpty()) {
            throw new IllegalArgumentException("At least one mirror id is required");
        }
        this.mirrorIds = Set.copyOf(new LinkedHashSet<>(mirrorIds));
    }

    public Set<String> getMirrorIds() {
        return mirrorIds;
    }

    @Override
    public ActionPhase getPhase() {
        return ActionPhase.MIRROR;
    }

    @Override
    public void execute(ActionContext context)
            throws ActionExecutionException {
        Objects.requireNonNull(context);
        try {
            context.getMirrorManager().deny(
                    context.getOriginalPath(),
                    mirrorIds
            );
        } catch (IOException | RuntimeException exception) {
            throw new ActionExecutionException(
                    "Failed to revoke file from mirrors " + mirrorIds,
                    exception
            );
        }
    }
}
