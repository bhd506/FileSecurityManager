package com.haydeproductions.project.rule.action;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class AllowMirrorAction implements Action {
    private final Set<String> mirrorIds;

    public AllowMirrorAction(Collection<String> mirrorIds) {
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

        // Quarantine/delete precede MIRROR. Never authorize a path whose scanned
        // file has already left the protected source tree during this action run.
        if (!context.isAtOriginalPath()
                || !Files.isRegularFile(
                        context.getOriginalPath(),
                        LinkOption.NOFOLLOW_LINKS
                )) {
            return;
        }

        try {
            context.getMirrorManager().allow(
                    context.getOriginalPath(),
                    mirrorIds
            );
        } catch (IOException | RuntimeException exception) {
            throw new ActionExecutionException(
                    "Failed to authorize file for mirrors " + mirrorIds,
                    exception
            );
        }
    }
}
