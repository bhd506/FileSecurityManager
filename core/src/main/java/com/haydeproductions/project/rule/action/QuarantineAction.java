package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.log.LogEntry;
import com.haydeproductions.project.log.LogEventType;
import com.haydeproductions.project.log.LogHandler;
import com.haydeproductions.project.quarantine.QuarantineResult;
import com.haydeproductions.project.quarantine.QuarantineService;
import com.haydeproductions.project.state.FileState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class QuarantineAction implements Action {

    private final QuarantineService quarantineService;
    private final LogHandler logHandler;

    public QuarantineAction(
            QuarantineService quarantineService,
            LogHandler logHandler
    ) {
        this.quarantineService =
                Objects.requireNonNull(quarantineService);
        this.logHandler = Objects.requireNonNull(logHandler);
    }

    @Override
    public ActionPhase getPhase() {
        return ActionPhase.QUARANTINE;
    }

    @Override
    public void execute(ActionContext context)
            throws ActionExecutionException {

        Objects.requireNonNull(context);

        Path originalPath = context.getOriginalPath();

        try {
            Optional<QuarantineResult> result =
                    quarantineService.quarantine(originalPath);

            if (result.isEmpty()) {
                logHandler.log(
                        LogEntry.create(
                                LogEventType.QUARANTINE_SKIPPED_MISSING,
                                originalPath,
                                context.getCurrentPath(),
                                "Quarantine skipped because the source file no longer exists"
                        )
                );
                return;
            }

            QuarantineResult quarantineResult = result.get();

            context.setCurrentPath(
                    quarantineResult.quarantinePath()
            );

            context.getStateRegistry().setState(
                    originalPath,
                    FileState.QUARANTINED
            );

            logHandler.log(
                    LogEntry.create(
                            LogEventType.QUARANTINE_SUCCESS,
                            originalPath,
                            quarantineResult.quarantinePath(),
                            "File quarantined successfully"
                    )
            );

        } catch (IOException exception) {
            logFailure(
                    context,
                    LogEventType.QUARANTINE_FAILED,
                    "Quarantine failed: " + exception.getMessage(),
                    exception
            );

            throw new ActionExecutionException(
                    "Failed to quarantine file: " + originalPath,
                    exception
            );
        }
    }

    private void logFailure(
            ActionContext context,
            LogEventType type,
            String message,
            IOException originalFailure
    ) {
        try {
            logHandler.log(
                    LogEntry.create(
                            type,
                            context.getOriginalPath(),
                            context.getCurrentPath(),
                            message
                    )
            );
        } catch (IOException loggingFailure) {
            originalFailure.addSuppressed(loggingFailure);
        }
    }
}
