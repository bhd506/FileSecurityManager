package com.haydeproductions.project;

import com.haydeproductions.project.runtime.FileSecurityApplication;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("config/config.yaml");

        Path sourceRoot = args.length > 1
                ? Path.of(args[1])
                : Path.of("test-data");

        Path quarantineRoot = args.length > 2
                ? Path.of(args[2])
                : Path.of("quarantine");

        Path logRoot = args.length > 3
                ? Path.of(args[3])
                : Path.of("logs");

        try (FileSecurityApplication application =
                     FileSecurityApplication.create(
                             configPath,
                             sourceRoot,
                             quarantineRoot,
                             logRoot
                     )) {

            Thread shutdownHook = new Thread(
                    application::close,
                    "file-security-shutdown"
            );

            Runtime.getRuntime().addShutdownHook(shutdownHook);

            application.start();

            System.out.println(
                    "File security runtime active on: "
                            + application.getConfig().getRoot()
            );

            application.getStatusApiAddress().ifPresent(address ->
                    System.out.println(
                            "Status API listening on: http://"
                                    + displayHost(address)
                                    + ":"
                                    + address.getPort()
                                    + "/api/v1/"
                    )
            );

            System.out.println(
                    "Initial tracked states: "
                            + application.getStateRegistry().snapshot()
            );

            try {
                new CountDownLatch(1).await();
            } finally {
                try {
                    Runtime.getRuntime()
                            .removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown already in progress.
                }
            }
        }
    }

    private static String displayHost(
            InetSocketAddress address
    ) {
        String host = address.getHostString();

        if ("0.0.0.0".equals(host)
                || "::".equals(host)) {
            return "localhost";
        }

        return host;
    }
}