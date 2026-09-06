package com.haydeproductions.project.status;

import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FileStatusService {

    private final Path sourceRoot;
    private final FileStateRegistry stateRegistry;

    public FileStatusService(
            Path sourceRoot,
            FileStateRegistry stateRegistry
    ) {
        this.sourceRoot = normalizeAbsolute(sourceRoot);
        this.stateRegistry = Objects.requireNonNull(stateRegistry);
    }

    public Path getSourceRoot() {
        return sourceRoot;
    }

    public FileStatus getStatus(String relativePath) {
        ResolvedPath resolved = resolve(relativePath, false);
        Optional<FileState> state = stateRegistry.getState(resolved.absolutePath());

        return state
                .map(value -> FileStatus.tracked(resolved.portablePath(), value))
                .orElseGet(() -> FileStatus.untracked(resolved.portablePath()));
    }

    public List<FileStatus> listStatuses() {
        return listStatusesInternal(null);
    }

    public List<FileStatus> listStatuses(String relativePrefix) {
        ResolvedPath prefix = resolve(relativePrefix, true);
        return listStatusesInternal(prefix.absolutePath());
    }

    private List<FileStatus> listStatusesInternal(Path absolutePrefix) {
        Map<Path, FileState> snapshot = stateRegistry.snapshot();
        List<FileStatus> statuses = new ArrayList<>();

        for (Map.Entry<Path, FileState> entry : snapshot.entrySet()) {
            Path absolutePath = normalizeAbsolute(entry.getKey());

            if (!absolutePath.startsWith(sourceRoot)) {
                continue;
            }

            if (absolutePrefix != null && !absolutePath.startsWith(absolutePrefix)) {
                continue;
            }

            Path relative = sourceRoot.relativize(absolutePath);
            if (relative.toString().isEmpty()) {
                continue;
            }

            statuses.add(
                    FileStatus.tracked(
                            portable(relative),
                            entry.getValue()
                    )
            );
        }

        statuses.sort(
                Comparator.comparing(FileStatus::path)
        );

        return List.copyOf(statuses);
    }

    private ResolvedPath resolve(
            String rawPath,
            boolean allowRoot
    ) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new InvalidStatusPathException(
                    "Path cannot be blank"
            );
        }

        final Path relative;
        try {
            relative = Path.of(rawPath);
        } catch (InvalidPathException exception) {
            throw new InvalidStatusPathException(
                    "Invalid path: " + rawPath,
                    exception
            );
        }

        if (relative.isAbsolute()) {
            throw new InvalidStatusPathException(
                    "Path must be relative to the configured source root"
            );
        }

        Path normalizedRelative = relative.normalize();
        Path absolute = sourceRoot
                .resolve(normalizedRelative)
                .normalize();

        if (!absolute.startsWith(sourceRoot)) {
            throw new InvalidStatusPathException(
                    "Path escapes the configured source root: " + rawPath
            );
        }

        Path canonicalRelative = sourceRoot.relativize(absolute);

        if (!allowRoot && canonicalRelative.toString().isEmpty()) {
            throw new InvalidStatusPathException(
                    "Path must identify a file beneath the configured source root"
            );
        }

        String portablePath = canonicalRelative.toString().isEmpty()
                ? "."
                : portable(canonicalRelative);

        return new ResolvedPath(
                absolute,
                portablePath
        );
    }

    private static Path normalizeAbsolute(Path path) {
        return Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record ResolvedPath(
            Path absolutePath,
            String portablePath
    ) {
    }
}
