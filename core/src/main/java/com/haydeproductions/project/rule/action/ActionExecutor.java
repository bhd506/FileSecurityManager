package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.mirror.MirrorManager;
import com.haydeproductions.project.scan.ScanSession;
import com.haydeproductions.project.scan.ScheduledAction;
import com.haydeproductions.project.state.FileStateRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ActionExecutor {

    private final FileStateRegistry stateRegistry;
    private final MirrorManager mirrorManager;

    public ActionExecutor(FileStateRegistry stateRegistry) {
        this(stateRegistry, MirrorManager.none());
    }

    public ActionExecutor(
            FileStateRegistry stateRegistry,
            MirrorManager mirrorManager
    ) {
        this.stateRegistry = Objects.requireNonNull(stateRegistry);
        this.mirrorManager = Objects.requireNonNull(mirrorManager);
    }

    public void execute(ScanSession session)
            throws ActionExecutionException {

        Objects.requireNonNull(session);

        ActionContext context =
                new ActionContext(
                        session.getFile(),
                        stateRegistry,
                        mirrorManager
                );

        List<ScheduledAction> ordered =
                session.getScheduledActions()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        scheduled ->
                                                scheduled.action()
                                                        .getPhase()
                                )
                        )
                        .toList();

        for (ScheduledAction scheduledAction : ordered) {
            scheduledAction.action().execute(context);
        }
    }
}