package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.scan.ScanSession;
import com.haydeproductions.project.scan.ScheduledAction;
import com.haydeproductions.project.state.FileStateRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ActionExecutor {

    private final FileStateRegistry stateRegistry;

    public ActionExecutor(FileStateRegistry stateRegistry) {
        this.stateRegistry =
                Objects.requireNonNull(stateRegistry);
    }

    public void execute(ScanSession session)
            throws ActionExecutionException {

        Objects.requireNonNull(session);

        ActionContext context =
                new ActionContext(
                        session.getFile(),
                        stateRegistry
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