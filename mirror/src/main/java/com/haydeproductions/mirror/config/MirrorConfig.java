package com.haydeproductions.mirror.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class MirrorConfig {
    private final Path sourceRoot;
    private final Path mirrorRoot;
    private final MirrorMode mode;
    private final TransferMode transferMode;
    private final ConflictPolicy conflictPolicy;
    private final Duration debounce;
    private final int maxTransferAttempts;
    private final Path stateFile;

    private MirrorConfig(Builder builder) {
        this.sourceRoot = normalise(builder.sourceRoot);
        this.mirrorRoot = normalise(builder.mirrorRoot);
        this.mode = builder.mode;
        this.transferMode = builder.transferMode;
        this.conflictPolicy = builder.conflictPolicy;
        this.debounce = builder.debounce;
        this.maxTransferAttempts = builder.maxTransferAttempts;
        this.stateFile = builder.stateFile == null ? null : normalise(builder.stateFile);

        validate();
    }

    public static Builder builder(Path sourceRoot, Path mirrorRoot) {
        return new Builder(sourceRoot, mirrorRoot);
    }

    private static Path normalise(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private void validate() {
        if (sourceRoot.equals(mirrorRoot)) {
            throw new IllegalArgumentException("Source and mirror roots must be different");
        }
        if (sourceRoot.startsWith(mirrorRoot) || mirrorRoot.startsWith(sourceRoot)) {
            throw new IllegalArgumentException("Source and mirror roots must not overlap");
        }
        if (transferMode == TransferMode.MOVE && mode != MirrorMode.ONE_TIME) {
            throw new IllegalArgumentException("MOVE is only supported with ONE_TIME mode");
        }
        if (debounce.isNegative()) {
            throw new IllegalArgumentException("Debounce duration cannot be negative");
        }
        if (maxTransferAttempts < 1) {
            throw new IllegalArgumentException("maxTransferAttempts must be at least 1");
        }
    }

    public Path sourceRoot() {
        return sourceRoot;
    }

    public Path mirrorRoot() {
        return mirrorRoot;
    }

    public MirrorMode mode() {
        return mode;
    }

    public TransferMode transferMode() {
        return transferMode;
    }

    public ConflictPolicy conflictPolicy() {
        return conflictPolicy;
    }

    public Duration debounce() {
        return debounce;
    }

    public int maxTransferAttempts() {
        return maxTransferAttempts;
    }

    public Optional<Path> stateFile() {
        return Optional.ofNullable(stateFile);
    }

    public static final class Builder {
        private final Path sourceRoot;
        private final Path mirrorRoot;
        private MirrorMode mode = MirrorMode.ONE_TIME;
        private TransferMode transferMode = TransferMode.COPY;
        private ConflictPolicy conflictPolicy = ConflictPolicy.FAIL;
        private Duration debounce = Duration.ofMillis(150);
        private int maxTransferAttempts = 3;
        private Path stateFile;

        private Builder(Path sourceRoot, Path mirrorRoot) {
            this.sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot");
            this.mirrorRoot = Objects.requireNonNull(mirrorRoot, "mirrorRoot");
        }

        public Builder mode(MirrorMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        public Builder transferMode(TransferMode transferMode) {
            this.transferMode = Objects.requireNonNull(transferMode, "transferMode");
            return this;
        }

        public Builder conflictPolicy(ConflictPolicy conflictPolicy) {
            this.conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
            return this;
        }

        public Builder debounce(Duration debounce) {
            this.debounce = Objects.requireNonNull(debounce, "debounce");
            return this;
        }

        public Builder maxTransferAttempts(int maxTransferAttempts) {
            this.maxTransferAttempts = maxTransferAttempts;
            return this;
        }

        public Builder stateFile(Path stateFile) {
            this.stateFile = Objects.requireNonNull(stateFile, "stateFile");
            return this;
        }

        public MirrorConfig build() {
            return new MirrorConfig(this);
        }
    }
}
