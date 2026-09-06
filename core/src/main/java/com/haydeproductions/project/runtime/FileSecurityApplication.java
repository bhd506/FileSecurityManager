package com.haydeproductions.project.runtime;

import com.haydeproductions.project.config.Config;
import com.haydeproductions.project.config.ConfigActionServices;
import com.haydeproductions.project.config.ConfigLoader;
import com.haydeproductions.project.config.StatusApiConfig;
import com.haydeproductions.project.file.FileFingerprintService;
import com.haydeproductions.project.log.FileLogHandler;
import com.haydeproductions.project.log.LogHandler;
import com.haydeproductions.project.mirror.MirrorManager;
import com.haydeproductions.project.quarantine.QuarantineService;
import com.haydeproductions.project.rule.action.ActionExecutor;
import com.haydeproductions.project.scan.FileScanner;
import com.haydeproductions.project.scan.RuleSetIndex;
import com.haydeproductions.project.scan.ScanCoordinator;
import com.haydeproductions.project.state.FileStateRegistry;
import com.haydeproductions.project.status.FileStatusService;
import com.haydeproductions.project.status.http.FileStatusHttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileSecurityApplication implements AutoCloseable {

    private final Config config;
    private final FileStateRegistry stateRegistry;
    private final FileStatusService statusService;
    private final FileSecurityRuntime runtime;
    private final MirrorManager mirrorManager;
    private final FileStatusHttpServer statusHttpServer;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private FileSecurityApplication(
            Config config,
            FileStateRegistry stateRegistry,
            FileStatusService statusService,
            FileSecurityRuntime runtime,
            MirrorManager mirrorManager,
            FileStatusHttpServer statusHttpServer
    ) {
        this.config = Objects.requireNonNull(config);
        this.stateRegistry = Objects.requireNonNull(stateRegistry);
        this.statusService = Objects.requireNonNull(statusService);
        this.runtime = Objects.requireNonNull(runtime);
        this.mirrorManager = Objects.requireNonNull(mirrorManager);
        this.statusHttpServer = statusHttpServer;
    }

    public static FileSecurityApplication create(
            Path configPath,
            Path sourceRoot,
            Path quarantineRoot,
            Path logRoot
    ) throws IOException {
        return create(
                configPath,
                sourceRoot,
                quarantineRoot,
                logRoot,
                Path.of("mirror-data")
        );
    }

    public static FileSecurityApplication create(
            Path configPath,
            Path sourceRoot,
            Path quarantineRoot,
            Path logRoot,
            Path mirrorRoot
    ) throws IOException {
        Objects.requireNonNull(configPath);
        Objects.requireNonNull(sourceRoot);
        Objects.requireNonNull(quarantineRoot);
        Objects.requireNonNull(logRoot);
        Objects.requireNonNull(mirrorRoot);

        Path normalizedSourceRoot = normalize(sourceRoot);
        Path normalizedQuarantineRoot = normalize(quarantineRoot);
        Path normalizedLogRoot = normalize(logRoot);
        Path normalizedMirrorRoot = normalize(mirrorRoot);

        validateOperationalRoots(
                normalizedSourceRoot,
                normalizedQuarantineRoot,
                normalizedLogRoot,
                normalizedMirrorRoot
        );

        LogHandler logHandler = new FileLogHandler(
                normalizedLogRoot.resolve("events.jsonl"),
                normalizedLogRoot.resolve("deletions.jsonl")
        );

        QuarantineService quarantineService =
                new QuarantineService(normalizedQuarantineRoot);

        Config config = ConfigLoader.load(
                configPath,
                normalizedSourceRoot,
                ConfigActionServices.of(
                        logHandler,
                        quarantineService
                )
        );

        validateRuleSetRoots(config);

        MirrorManager mirrorManager = MirrorManager.create(
                config.getRoot(),
                normalizedMirrorRoot,
                normalizedLogRoot.resolve("mirror-state"),
                config.getMirrors(),
                logHandler
        );

        FileStateRegistry stateRegistry = new FileStateRegistry();
        RuleSetIndex ruleSetIndex = new RuleSetIndex(config.getRuleSets());
        ScanCoordinator scanCoordinator = new ScanCoordinator(ruleSetIndex);
        ActionExecutor actionExecutor = new ActionExecutor(
                stateRegistry,
                mirrorManager
        );
        FileScanner fileScanner = new FileScanner(
                scanCoordinator,
                actionExecutor,
                stateRegistry,
                new FileFingerprintService(),
                mirrorManager
        );

        FileSecurityRuntime runtime = new FileSecurityRuntime(
                config.getRoot(),
                fileScanner,
                stateRegistry,
                logHandler,
                config.getRuntime().debounce(),
                config.getRuntime().workerThreads(),
                mirrorManager
        );

        FileStatusService statusService = new FileStatusService(
                config.getRoot(),
                stateRegistry
        );

        FileStatusHttpServer statusHttpServer = null;
        StatusApiConfig statusApi = config.getStatusApi();
        if (statusApi.enabled()) {
            statusHttpServer = new FileStatusHttpServer(
                    new InetSocketAddress(
                            statusApi.host(),
                            statusApi.port()
                    ),
                    statusService
            );
        }

        return new FileSecurityApplication(
                config,
                stateRegistry,
                statusService,
                runtime,
                mirrorManager,
                statusHttpServer
        );
    }

    public Config getConfig() {
        return config;
    }

    public FileStateRegistry getStateRegistry() {
        return stateRegistry;
    }

    public FileStatusService getStatusService() {
        return statusService;
    }

    public MirrorManager getMirrorManager() {
        return mirrorManager;
    }

    public boolean isRunning() {
        return started.get()
                && !closed.get()
                && runtime.isRunning();
    }

    public Optional<InetSocketAddress> getStatusApiAddress() {
        return Optional.ofNullable(statusHttpServer)
                .map(FileStatusHttpServer::getAddress);
    }

    public synchronized void start() throws IOException {
        ensureOpen();
        if (!started.compareAndSet(false, true)) {
            return;
        }

        try {
            // Mirror source-side watching is disabled in security-managed mode,
            // so starting mirrors first cannot bypass scanning. It does close the
            // mirror-side startup race before the initial source scan authorizes targets.
            mirrorManager.start();
            runtime.start();
            if (statusHttpServer != null) {
                statusHttpServer.start();
            }
        } catch (IOException | RuntimeException exception) {
            close();
            throw exception;
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        if (statusHttpServer != null) {
            statusHttpServer.close();
        }
        runtime.close();
        mirrorManager.close();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("FileSecurityApplication is closed");
        }
    }

    private static void validateRuleSetRoots(Config config) throws IOException {
        for (var ruleSet : config.getRuleSets()) {
            Path root = ruleSet.getRoot();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root)) {
                throw new IOException(
                        "Configured RuleSet root must exist as a real directory: " + root
                );
            }
        }
    }

    private static void validateOperationalRoots(Path... roots)
            throws IOException {
        List<Path> values = new ArrayList<>(List.of(roots));
        for (int left = 0; left < values.size(); left++) {
            for (int right = left + 1; right < values.size(); right++) {
                Path a = values.get(left);
                Path b = values.get(right);
                if (a.equals(b) || a.startsWith(b) || b.startsWith(a)) {
                    throw new IOException(
                            "Runtime data roots must not overlap: " + a + " and " + b
                    );
                }
            }
        }
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path).toAbsolutePath().normalize();
    }
}
