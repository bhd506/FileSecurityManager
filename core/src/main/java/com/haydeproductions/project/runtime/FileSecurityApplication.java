package com.haydeproductions.project.runtime;

import com.haydeproductions.project.config.Config;
import com.haydeproductions.project.config.ConfigActionServices;
import com.haydeproductions.project.config.ConfigLoader;
import com.haydeproductions.project.config.StatusApiConfig;
import com.haydeproductions.project.log.FileLogHandler;
import com.haydeproductions.project.log.LogHandler;
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
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileSecurityApplication implements AutoCloseable {

    private final Config config;
    private final FileStateRegistry stateRegistry;
    private final FileStatusService statusService;
    private final FileSecurityRuntime runtime;
    private final FileStatusHttpServer statusHttpServer;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private FileSecurityApplication(
            Config config,
            FileStateRegistry stateRegistry,
            FileStatusService statusService,
            FileSecurityRuntime runtime,
            FileStatusHttpServer statusHttpServer
    ) {
        this.config = Objects.requireNonNull(config);
        this.stateRegistry = Objects.requireNonNull(stateRegistry);
        this.statusService = Objects.requireNonNull(statusService);
        this.runtime = Objects.requireNonNull(runtime);
        this.statusHttpServer = statusHttpServer;
    }

    public static FileSecurityApplication create(
            Path configPath,
            Path sourceRoot,
            Path quarantineRoot,
            Path logRoot
    ) throws IOException {
        Objects.requireNonNull(configPath);
        Objects.requireNonNull(sourceRoot);
        Objects.requireNonNull(quarantineRoot);
        Objects.requireNonNull(logRoot);

        Path normalizedLogRoot = logRoot
                .toAbsolutePath()
                .normalize();

        LogHandler logHandler = new FileLogHandler(
                normalizedLogRoot.resolve("events.jsonl"),
                normalizedLogRoot.resolve("deletions.jsonl")
        );

        QuarantineService quarantineService =
                new QuarantineService(quarantineRoot);

        Config config = ConfigLoader.load(
                configPath,
                sourceRoot,
                ConfigActionServices.of(
                        logHandler,
                        quarantineService
                )
        );

        FileStateRegistry stateRegistry =
                new FileStateRegistry();

        RuleSetIndex ruleSetIndex =
                new RuleSetIndex(config.getRuleSets());

        ScanCoordinator scanCoordinator =
                new ScanCoordinator(ruleSetIndex);

        ActionExecutor actionExecutor =
                new ActionExecutor(stateRegistry);

        FileScanner fileScanner = new FileScanner(
                scanCoordinator,
                actionExecutor,
                stateRegistry
        );

        FileSecurityRuntime runtime =
                new FileSecurityRuntime(
                        config.getRoot(),
                        fileScanner,
                        stateRegistry,
                        logHandler
                );

        FileStatusService statusService =
                new FileStatusService(
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
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "FileSecurityApplication is closed"
            );
        }
    }
}
