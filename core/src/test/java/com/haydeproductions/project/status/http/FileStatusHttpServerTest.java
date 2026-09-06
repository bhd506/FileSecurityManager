package com.haydeproductions.project.status.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haydeproductions.project.state.FileState;
import com.haydeproductions.project.state.FileStateRegistry;
import com.haydeproductions.project.status.FileStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStatusHttpServerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void singleStateEndpointReturnsTrackedState() throws Exception {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(root.resolve("uploads/file.txt"), FileState.SAFE);

        try (FileStatusHttpServer server = server(root, registry)) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/state?path=" + encode("uploads/file.txt")
            );

            assertEquals(200, response.statusCode());

            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            assertEquals("uploads/file.txt", json.get("path").asText());
            assertEquals("SAFE", json.get("state").asText());
            assertTrue(json.get("tracked").asBoolean());
            assertTrue(json.get("safe").asBoolean());
        }
    }

    @Test
    void singleStateEndpointReturnsUntrackedInsteadOf404() throws Exception {
        Path root = tempDir.resolve("data");

        try (FileStatusHttpServer server = server(root, new FileStateRegistry())) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/state?path=" + encode("missing.txt")
            );

            assertEquals(200, response.statusCode());

            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            assertEquals("UNTRACKED", json.get("state").asText());
            assertFalse(json.get("tracked").asBoolean());
            assertFalse(json.get("safe").asBoolean());
        }
    }

    @Test
    void missingPathParameterReturnsBadRequest() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/state"
            );

            assertEquals(400, response.statusCode());
            assertEquals(
                    "INVALID_REQUEST",
                    OBJECT_MAPPER.readTree(response.body()).get("error").asText()
            );
        }
    }

    @Test
    void duplicatePathParameterReturnsBadRequest() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/state?path=a.txt&path=b.txt"
            );

            assertEquals(400, response.statusCode());
        }
    }

    @Test
    void unknownQueryParameterReturnsBadRequest() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/state?path=a.txt&extra=true"
            );

            assertEquals(400, response.statusCode());
        }
    }

    @Test
    void encodedTraversalIsRejected() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/state?path=" + encode("../outside.txt")
            );

            assertEquals(400, response.statusCode());
        }
    }

    @Test
    void statesEndpointReturnsSortedTrackedFiles() throws Exception {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(root.resolve("z.txt"), FileState.FLAGGED);
        registry.setState(root.resolve("a.txt"), FileState.SAFE);

        try (FileStatusHttpServer server = server(root, registry)) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/states"
            );

            assertEquals(200, response.statusCode());

            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            assertEquals(2, json.get("count").asInt());
            assertEquals("a.txt", json.get("files").get(0).get("path").asText());
            assertEquals("z.txt", json.get("files").get(1).get("path").asText());
        }
    }

    @Test
    void statesEndpointSupportsPrefixFilter() throws Exception {
        Path root = tempDir.resolve("data");
        FileStateRegistry registry = new FileStateRegistry();
        registry.setState(root.resolve("uploads/a.txt"), FileState.SAFE);
        registry.setState(root.resolve("other/b.txt"), FileState.FLAGGED);

        try (FileStatusHttpServer server = server(root, registry)) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/states?prefix=" + encode("uploads")
            );

            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            assertEquals(1, json.get("count").asInt());
            assertEquals(
                    "uploads/a.txt",
                    json.get("files").get(0).get("path").asText()
            );
        }
    }

    @Test
    void statesEndpointRejectsEscapingPrefix() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/states?prefix=" + encode("../outside")
            );

            assertEquals(400, response.statusCode());
        }
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/health"
            );

            assertEquals(200, response.statusCode());
            assertEquals(
                    "UP",
                    OBJECT_MAPPER.readTree(response.body()).get("status").asText()
            );
        }
    }

    @Test
    void nonGetMethodReturnsMethodNotAllowed() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpRequest request = HttpRequest.newBuilder(
                            uri(server, "/api/v1/health")
                    )
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(405, response.statusCode());
            assertEquals("GET", response.headers().firstValue("Allow").orElseThrow());
        }
    }

    @Test
    void unknownEndpointReturnsNotFound() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/unknown"
            );

            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void endpointSubpathDoesNotAccidentallyMatch() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(
                    server,
                    "/api/v1/files/state/extra?path=a.txt"
            );

            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void responsesAreJsonAndNotCacheable() throws Exception {
        try (FileStatusHttpServer server = server()) {
            server.start();

            HttpResponse<String> response = get(server, "/api/v1/health");

            assertTrue(
                    response.headers()
                            .firstValue("Content-Type")
                            .orElseThrow()
                            .startsWith("application/json")
            );
            assertEquals(
                    "no-store",
                    response.headers().firstValue("Cache-Control").orElseThrow()
            );
        }
    }

    @Test
    void startIsIdempotentAndCloseStopsServer() throws Exception {
        FileStatusHttpServer server = server();

        server.start();
        server.start();

        assertTrue(server.isRunning());
        assertEquals(200, get(server, "/api/v1/health").statusCode());

        server.close();
        server.close();

        assertFalse(server.isRunning());
        assertThrows(
                IllegalStateException.class,
                server::start
        );
    }

    private FileStatusHttpServer server() throws Exception {
        return server(
                tempDir.resolve("data"),
                new FileStateRegistry()
        );
    }

    private FileStatusHttpServer server(
            Path root,
            FileStateRegistry registry
    ) throws Exception {
        return new FileStatusHttpServer(
                new InetSocketAddress("127.0.0.1", 0),
                new FileStatusService(root, registry)
        );
    }

    private HttpResponse<String> get(
            FileStatusHttpServer server,
            String pathAndQuery
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        uri(server, pathAndQuery)
                )
                .GET()
                .build();

        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private URI uri(
            FileStatusHttpServer server,
            String pathAndQuery
    ) {
        return URI.create(
                "http://127.0.0.1:"
                        + server.getPort()
                        + pathAndQuery
        );
    }

    private String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
}
