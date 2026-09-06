package com.haydeproductions.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void mainStartsRuntimeAndStatusApiUsingExplicitArguments()
            throws Exception {

        Path source = Files.createDirectories(
                tempDir.resolve("data")
        );

        Path quarantine =
                tempDir.resolve("quarantine");

        Path logs =
                tempDir.resolve("logs");

        Path file =
                source.resolve("example.txt");

        Files.writeString(
                file,
                "safe contents"
        );

        int port = findFreePort();

        Path config = writeConfig("""
                version: 1

                statusApi:
                  enabled: true
                  host: "127.0.0.1"
                  port: %d

                ruleSets:
                  - path: "."
                """.formatted(port));

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        PrintStream originalOut = System.out;

        AtomicReference<Throwable> failure =
                new AtomicReference<>();

        Thread mainThread = new Thread(
                () -> {
                    try {
                        Main.main(
                                new String[]{
                                        config.toString(),
                                        source.toString(),
                                        quarantine.toString(),
                                        logs.toString()
                                }
                        );
                    } catch (InterruptedException ignored) {
                        // Expected when the test stops Main.
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    }
                },
                "main-test-thread"
        );

        try {
            System.setOut(
                    new PrintStream(
                            output,
                            true,
                            StandardCharsets.UTF_8
                    )
            );

            mainThread.start();

            waitForApi(port);

            HttpResponse<String> response =
                    queryFileState(
                            port,
                            "example.txt"
                    );

            assertEquals(
                    200,
                    response.statusCode()
            );

            JsonNode json =
                    OBJECT_MAPPER.readTree(
                            response.body()
                    );

            assertEquals(
                    "SAFE",
                    json.get("state").asText()
            );

            assertTrue(
                    json.get("tracked").asBoolean()
            );

            assertTrue(
                    json.get("safe").asBoolean()
            );

            String stdout =
                    output.toString(
                            StandardCharsets.UTF_8
                    );

            assertTrue(
                    stdout.contains(
                            "File security runtime active on:"
                    )
            );

            assertTrue(
                    stdout.contains(
                            "Status API listening on:"
                    )
            );

            assertTrue(
                    Files.exists(logs)
            );

            assertTrue(
                    Files.exists(quarantine)
                            || !Files.exists(quarantine)
            );

            assertNull(
                    failure.get(),
                    () -> "Main failed with: "
                            + failure.get()
            );

        } finally {
            mainThread.interrupt();

            mainThread.join(5_000);

            System.setOut(originalOut);
        }

        assertFalse(
                mainThread.isAlive(),
                "Main thread did not terminate after interruption"
        );

        assertNull(
                failure.get(),
                () -> "Main failed with: "
                        + failure.get()
        );
    }

    @Test
    void mainRunsWithStatusApiDisabled()
            throws Exception {

        Path source = Files.createDirectories(
                tempDir.resolve("data-disabled")
        );

        Path quarantine =
                tempDir.resolve("quarantine-disabled");

        Path logs =
                tempDir.resolve("logs-disabled");

        Path config = writeConfig("""
                version: 1

                statusApi:
                  enabled: false

                ruleSets:
                  - path: "."
                """);

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        PrintStream originalOut = System.out;

        AtomicReference<Throwable> failure =
                new AtomicReference<>();

        Thread mainThread = new Thread(
                () -> {
                    try {
                        Main.main(
                                new String[]{
                                        config.toString(),
                                        source.toString(),
                                        quarantine.toString(),
                                        logs.toString()
                                }
                        );
                    } catch (InterruptedException ignored) {
                        // Expected shutdown path.
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    }
                },
                "main-disabled-api-test-thread"
        );

        try {
            System.setOut(
                    new PrintStream(
                            output,
                            true,
                            StandardCharsets.UTF_8
                    )
            );

            mainThread.start();

            waitForOutput(
                    output,
                    "File security runtime active on:"
            );

            String stdout =
                    output.toString(
                            StandardCharsets.UTF_8
                    );

            assertTrue(
                    stdout.contains(
                            "File security runtime active on:"
                    )
            );

            assertFalse(
                    stdout.contains(
                            "Status API listening on:"
                    )
            );

            assertNull(
                    failure.get(),
                    () -> "Main failed with: "
                            + failure.get()
            );

        } finally {
            mainThread.interrupt();

            mainThread.join(5_000);

            System.setOut(originalOut);
        }

        assertFalse(
                mainThread.isAlive()
        );
    }

    @Test
    void mainPropagatesMissingConfigurationFailure()
            throws Exception {

        Path source = Files.createDirectories(
                tempDir.resolve("missing-config-data")
        );

        Path missingConfig =
                tempDir.resolve("does-not-exist.yaml");

        assertThrows(
                java.io.IOException.class,
                () -> Main.main(
                        new String[]{
                                missingConfig.toString(),
                                source.toString(),
                                tempDir.resolve("quarantine").toString(),
                                tempDir.resolve("logs").toString()
                        }
                )
        );
    }

    private Path writeConfig(String yaml)
            throws Exception {

        Path config =
                tempDir.resolve(
                        "config-"
                                + System.nanoTime()
                                + ".yaml"
                );

        Files.writeString(
                config,
                yaml
        );

        return config;
    }

    private int findFreePort()
            throws Exception {

        try (ServerSocket socket =
                     new ServerSocket(0)) {

            return socket.getLocalPort();
        }
    }

    private void waitForApi(int port)
            throws Exception {

        URI uri = URI.create(
                "http://127.0.0.1:"
                        + port
                        + "/api/v1/health"
        );

        HttpClient client =
                HttpClient.newHttpClient();

        long deadline =
                System.nanoTime()
                        + Duration.ofSeconds(5)
                        .toNanos();

        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> response =
                        client.send(
                                HttpRequest.newBuilder(uri)
                                        .GET()
                                        .build(),
                                HttpResponse
                                        .BodyHandlers
                                        .ofString()
                        );

                if (response.statusCode() == 200) {
                    return;
                }

            } catch (Exception ignored) {
                // Runtime may still be starting.
            }

            Thread.sleep(25);
        }

        fail("Status API did not start within timeout");
    }

    private HttpResponse<String> queryFileState(
            int port,
            String relativePath
    ) throws Exception {

        String encoded =
                URLEncoder.encode(
                        relativePath,
                        StandardCharsets.UTF_8
                );

        URI uri = URI.create(
                "http://127.0.0.1:"
                        + port
                        + "/api/v1/files/state?path="
                        + encoded
        );

        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(uri)
                                .GET()
                                .build(),
                        HttpResponse
                                .BodyHandlers
                                .ofString()
                );
    }

    private void waitForOutput(
            ByteArrayOutputStream output,
            String expected
    ) throws Exception {

        long deadline =
                System.nanoTime()
                        + Duration.ofSeconds(5)
                        .toNanos();

        while (System.nanoTime() < deadline) {
            if (output.toString(
                    StandardCharsets.UTF_8
            ).contains(expected)) {
                return;
            }

            Thread.sleep(25);
        }

        fail(
                "Expected output was not produced: "
                        + expected
        );
    }
}