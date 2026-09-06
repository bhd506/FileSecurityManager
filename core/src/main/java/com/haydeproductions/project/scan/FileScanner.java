package com.haydeproductions.project.scan;

import com.haydeproductions.project.file.FileFingerprint;
import com.haydeproductions.project.file.FileFingerprintService;
import com.haydeproductions.project.mirror.MirrorManager;
import com.haydeproductions.project.rule.action.ActionExecutionException;
import com.haydeproductions.project.rule.action.ActionExecutor;
import com.haydeproductions.project.rule.condition.ConditionEvaluationException;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class FileScanner {

    private final ScanCoordinator scanCoordinator;
    private final ActionExecutor actionExecutor;
    private final FileStateRegistry stateRegistry;
    private final FileFingerprintService fingerprintService;
    private final MirrorManager mirrorManager;

    public FileScanner(
            ScanCoordinator scanCoordinator,
            ActionExecutor actionExecutor,
            FileStateRegistry stateRegistry
    ) {
        this(
                scanCoordinator,
                actionExecutor,
                stateRegistry,
                new FileFingerprintService(),
                MirrorManager.none()
        );
    }

    public FileScanner(
            ScanCoordinator scanCoordinator,
            ActionExecutor actionExecutor,
            FileStateRegistry stateRegistry,
            FileFingerprintService fingerprintService
    ) {
        this(
                scanCoordinator,
                actionExecutor,
                stateRegistry,
                fingerprintService,
                MirrorManager.none()
        );
    }

    public FileScanner(
            ScanCoordinator scanCoordinator,
            ActionExecutor actionExecutor,
            FileStateRegistry stateRegistry,
            FileFingerprintService fingerprintService,
            MirrorManager mirrorManager
    ) {
        this.scanCoordinator = Objects.requireNonNull(scanCoordinator);
        this.actionExecutor = Objects.requireNonNull(actionExecutor);
        this.stateRegistry = Objects.requireNonNull(stateRegistry);
        this.fingerprintService = Objects.requireNonNull(fingerprintService);
        this.mirrorManager = Objects.requireNonNull(mirrorManager);
    }

    public ScanSession scan(Path file)
            throws ConditionEvaluationException,
            ActionExecutionException,
            FileScanException {

        Path normalizedFile = Objects.requireNonNull(file)
                .toAbsolutePath()
                .normalize();

        try {
            // Authorization is version-scoped. A previously approved version must
            // stop being an active mirror target before the new version is checked.
            mirrorManager.revokeAll(normalizedFile);
        } catch (IOException | RuntimeException exception) {
            stateRegistry.setState(normalizedFile, FileState.ERROR);
            throw new FileScanException(
                    "Failed to revoke stale mirror authorization: " + normalizedFile,
                    exception
            );
        }

        stateRegistry.setState(
                normalizedFile,
                FileState.SCANNING
        );

        try {
            Optional<FileFingerprint> before =
                    captureInitialFingerprint(normalizedFile);

            ScanSession session =
                    scanCoordinator.scan(normalizedFile);

            verifyUnchangedBeforeActions(
                    normalizedFile,
                    before
            );

            actionExecutor.execute(session);

            stateRegistry.compareAndSet(
                    normalizedFile,
                    FileState.SCANNING,
                    FileState.SAFE
            );

            return session;

        } catch (FileChangedDuringScanException exception) {
            throw exception;

        } catch (IOException exception) {
            stateRegistry.compareAndSet(
                    normalizedFile,
                    FileState.SCANNING,
                    FileState.ERROR
            );

            throw new FileScanException(
                    "Failed to verify file while scanning: " + normalizedFile,
                    exception
            );

        } catch (
                ConditionEvaluationException
                | ActionExecutionException exception
        ) {
            /*
             * Only an unchanged SCANNING state becomes ERROR. If a real
             * action already transitioned the file, that more meaningful
             * state is preserved. A delete action may already have removed
             * the state entirely.
             */
            stateRegistry.compareAndSet(
                    normalizedFile,
                    FileState.SCANNING,
                    FileState.ERROR
            );

            throw exception;
        }
    }

    private Optional<FileFingerprint> captureInitialFingerprint(
            Path file
    ) throws IOException, FileChangedDuringScanException {

        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            /*
             * Keeps FileScanner usable as a pure unit-level component with
             * synthetic Paths. The runtime only dispatches existing regular
             * files to it.
             */
            return Optional.empty();
        }

        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    fingerprintService.fingerprint(file)
            );
        } catch (NoSuchFileException exception) {
            stateRegistry.remove(file);
            throw new FileChangedDuringScanException(file);
        }
    }

    private void verifyUnchangedBeforeActions(
            Path file,
            Optional<FileFingerprint> before
    ) throws IOException, FileChangedDuringScanException {

        if (before.isEmpty()) {
            return;
        }

        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            stateRegistry.remove(file);
            throw new FileChangedDuringScanException(file);
        }

        FileFingerprint after;

        try {
            after = fingerprintService.fingerprint(file);
        } catch (NoSuchFileException exception) {
            stateRegistry.remove(file);
            throw new FileChangedDuringScanException(file);
        }

        if (!before.get().equals(after)) {
            stateRegistry.compareAndSet(
                    file,
                    FileState.SCANNING,
                    FileState.UNSCANNED
            );

            throw new FileChangedDuringScanException(file);
        }
    }
}
