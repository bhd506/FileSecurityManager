package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.file.FileFingerprint;
import com.haydeproductions.project.file.FileFingerprintService;
import com.haydeproductions.project.log.DeletionRecord;
import com.haydeproductions.project.log.LogEntry;
import com.haydeproductions.project.log.LogEventType;
import com.haydeproductions.project.log.LogHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

public final class DeleteAction implements Action {

    private final LogHandler logHandler;
    private final FileFingerprintService fingerprintService;

    public DeleteAction(LogHandler logHandler) {
        this(logHandler, new FileFingerprintService());
    }

    public DeleteAction(
            LogHandler logHandler,
            FileFingerprintService fingerprintService
    ) {
        this.logHandler = Objects.requireNonNull(logHandler);
        this.fingerprintService =
                Objects.requireNonNull(fingerprintService);
    }

    @Override
    public ActionPhase getPhase() {
        return ActionPhase.DELETE;
    }

    @Override
    public void execute(ActionContext context)
            throws ActionExecutionException {

        Objects.requireNonNull(context);

        Path originalPath = context.getOriginalPath();
        Path deletionPath = context.getCurrentPath();

        try {
            if (!Files.exists(deletionPath, LinkOption.NOFOLLOW_LINKS)) {
                handleMissing(context, deletionPath);
                return;
            }

            if (!Files.isRegularFile(deletionPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(
                        "Delete target is not a regular file: " + deletionPath
                );
            }

            FileFingerprint fingerprint =
                    fingerprintService.fingerprint(deletionPath);

            try {
                Files.delete(deletionPath);
            } catch (NoSuchFileException exception) {
                handleMissing(context, deletionPath);
                return;
            }

            context.getStateRegistry().remove(originalPath);

            DeletionRecord deletionRecord =
                    DeletionRecord.create(
                            originalPath,
                            deletionPath,
                            fingerprint.size(),
                            fingerprint.sha256()
                    );

            logHandler.logDeletion(deletionRecord);

            logHandler.log(
                    LogEntry.create(
                            LogEventType.DELETE_SUCCESS,
                            originalPath,
                            deletionPath,
                            "File deleted successfully"
                    )
            );

        } catch (IOException exception) {
            logFailure(
                    context,
                    deletionPath,
                    exception
            );

            throw new ActionExecutionException(
                    "Failed to delete file: " + deletionPath,
                    exception
            );
        }
    }

    private void handleMissing(
            ActionContext context,
            Path deletionPath
    ) throws IOException {
        context.getStateRegistry().remove(
                context.getOriginalPath()
        );

        logHandler.log(
                LogEntry.create(
                        LogEventType.DELETE_SKIPPED_MISSING,
                        context.getOriginalPath(),
                        deletionPath,
                        "Delete skipped because the target file no longer exists"
                )
        );
    }

    private void logFailure(
            ActionContext context,
            Path deletionPath,
            IOException originalFailure
    ) {
        try {
            logHandler.log(
                    LogEntry.create(
                            LogEventType.DELETE_FAILED,
                            context.getOriginalPath(),
                            deletionPath,
                            "Delete failed: " + originalFailure.getMessage()
                    )
            );
        } catch (IOException loggingFailure) {
            originalFailure.addSuppressed(loggingFailure);
        }
    }
}
