package com.haydeproductions.project.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haydeproductions.project.state.FileState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileSecurityApplicationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void disabledStatusApiDoesNotCreateHttpEndpoint() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("data"));
        Path config = writeConfig("{}\n");

        try (FileSecurityApplication application =
                     create(config, source)) {

            assertTrue(application.getStatusApiAddress().isEmpty());

            application.start();

            assertTrue(application.isRunning());
        }
    }

    @Test
    void enabledStatusApiIsQueryableAfterStartup() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("data"));
        Path file = source.resolve("file.txt");
        Files.writeString(file, "safe contents");

        Path config = writeConfig("""
                statusApi:
                  enabled: true
                  host: "127.0.0.1"
                  port: 0
                """);

        try (FileSecurityApplication application =
                     create(config, source)) {

            application.start();

            InetSocketAddress address = application
                    .getStatusApiAddress()
                    .orElseThrow();

            String encoded = URLEncoder.encode(
                    "file.txt",
                    StandardCharsets.UTF_8
            );

            URI uri = URI.create(
                    "http://127.0.0.1:"
                            + address.getPort()
                            + "/api/v1/files/state?path="
                            + encoded
            );

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());

            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            assertEquals("SAFE", json.get("state").asText());
            assertTrue(json.get("tracked").asBoolean());

            assertEquals(
                    Optional.of(FileState.SAFE),
                    application.getStateRegistry().getState(file)
            );
        }
    }

    @Test
    void internalStatusServiceUsesSameLiveRegistryAsRuntime() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("data"));
        Path file = source.resolve("file.txt");
        Files.writeString(file, "contents");

        Path config = writeConfig("{}\n");

        try (FileSecurityApplication application =
                     create(config, source)) {

            application.start();

            assertEquals(
                    "SAFE",
                    application.getStatusService()
                            .getStatus("file.txt")
                            .getStateName()
            );
        }
    }

    @Test
    void applicationCanBeStartedTwiceAndClosedTwice() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("data"));
        Path config = writeConfig("{}\n");

        FileSecurityApplication application = create(config, source);

        application.start();
        application.start();
        assertTrue(application.isRunning());

        application.close();
        application.close();
        assertFalse(application.isRunning());
    }

    @Test
    void closedApplicationCannotBeStarted() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("data"));
        Path config = writeConfig("{}\n");

        FileSecurityApplication application = create(config, source);
        application.close();

        assertThrows(
                IllegalStateException.class,
                application::start
        );
    }

    @Test
    void missingConfigPropagatesIOException() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("data"));

        assertThrows(
                java.io.IOException.class,
                () -> create(tempDir.resolve("missing.yaml"), source)
        );
    }

    private FileSecurityApplication create(
            Path config,
            Path source
    ) throws Exception {
        return FileSecurityApplication.create(
                config,
                source,
                tempDir.resolve("quarantine"),
                tempDir.resolve("logs")
        );
    }

    private Path writeConfig(String yaml) throws Exception {
        Path file = tempDir.resolve(
                "config-" + System.nanoTime() + ".yaml"
        );
        Files.writeString(file, yaml);
        return file;
    }
}
