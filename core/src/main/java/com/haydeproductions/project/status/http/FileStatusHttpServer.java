package com.haydeproductions.project.status.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haydeproductions.project.status.FileStatus;
import com.haydeproductions.project.status.FileStatusService;
import com.haydeproductions.project.status.InvalidStatusPathException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileStatusHttpServer implements AutoCloseable {

    public static final String STATE_ENDPOINT = "/api/v1/files/state";
    public static final String STATES_ENDPOINT = "/api/v1/files/states";
    public static final String HEALTH_ENDPOINT = "/api/v1/health";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FileStatusService statusService;
    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public FileStatusHttpServer(
            InetSocketAddress address,
            FileStatusService statusService
    ) throws IOException {
        this.statusService = Objects.requireNonNull(statusService);
        this.server = HttpServer.create(
                Objects.requireNonNull(address),
                0
        );

        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "file-status-http"
            );
            thread.setDaemon(true);
            return thread;
        });

        server.setExecutor(executor);
        server.createContext(STATE_ENDPOINT, this::handleState);
        server.createContext(STATES_ENDPOINT, this::handleStates);
        server.createContext(HEALTH_ENDPOINT, this::handleHealth);
        server.createContext("/api/v1/", this::handleNotFound);
    }

    public InetSocketAddress getAddress() {
        return server.getAddress();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public boolean isRunning() {
        return started.get() && !closed.get();
    }

    public synchronized void start() {
        ensureOpen();

        if (!started.compareAndSet(false, true)) {
            return;
        }

        server.start();
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        if (started.get()) {
            server.stop(0);
        }

        executor.shutdownNow();
    }

    private void handleState(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, STATE_ENDPOINT)) {
            sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            return;
        }

        if (!requireGet(exchange)) {
            return;
        }

        try {
            Map<String, List<String>> query = parseQuery(exchange.getRequestURI());
            requireOnly(query, Set.of("path"));
            String path = requireSingle(query, "path");

            FileStatus status = statusService.getStatus(path);
            sendJson(exchange, 200, statusBody(status));

        } catch (BadRequestException | InvalidStatusPathException exception) {
            sendError(exchange, 400, "INVALID_REQUEST", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "INTERNAL_ERROR", "Unable to query file state");
        }
    }

    private void handleStates(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, STATES_ENDPOINT)) {
            sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            return;
        }

        if (!requireGet(exchange)) {
            return;
        }

        try {
            Map<String, List<String>> query = parseQuery(exchange.getRequestURI());
            requireOnly(query, Set.of("prefix"));

            List<FileStatus> statuses;

            if (query.containsKey("prefix")) {
                statuses = statusService.listStatuses(
                        requireSingle(query, "prefix")
                );
            } else {
                statuses = statusService.listStatuses();
            }

            List<Map<String, Object>> files = statuses
                    .stream()
                    .map(this::statusBody)
                    .toList();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", files.size());
            body.put("files", files);

            sendJson(exchange, 200, body);

        } catch (BadRequestException | InvalidStatusPathException exception) {
            sendError(exchange, 400, "INVALID_REQUEST", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "INTERNAL_ERROR", "Unable to query file states");
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, HEALTH_ENDPOINT)) {
            sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            return;
        }

        if (!requireGet(exchange)) {
            return;
        }

        try {
            Map<String, List<String>> query = parseQuery(exchange.getRequestURI());
            requireOnly(query, Set.of());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "UP");
            sendJson(exchange, 200, body);

        } catch (BadRequestException exception) {
            sendError(exchange, 400, "INVALID_REQUEST", exception.getMessage());
        }
    }

    private void handleNotFound(HttpExchange exchange) throws IOException {
        sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
    }

    private boolean exactPath(HttpExchange exchange, String expected) {
        return expected.equals(exchange.getRequestURI().getPath());
    }

    private boolean requireGet(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }

        exchange.getResponseHeaders().set("Allow", "GET");
        sendError(
                exchange,
                405,
                "METHOD_NOT_ALLOWED",
                "Only GET is supported"
        );
        return false;
    }

    private Map<String, List<String>> parseQuery(URI uri) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String rawQuery = uri.getRawQuery();

        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }

        for (String part : rawQuery.split("&", -1)) {
            if (part.isEmpty()) {
                continue;
            }

            int equals = part.indexOf('=');
            String rawName = equals >= 0
                    ? part.substring(0, equals)
                    : part;
            String rawValue = equals >= 0
                    ? part.substring(equals + 1)
                    : "";

            String name = decode(rawName);
            String value = decode(rawValue);

            if (name.isBlank()) {
                throw new BadRequestException("Query parameter name cannot be blank");
            }

            result.computeIfAbsent(
                    name,
                    ignored -> new ArrayList<>()
            ).add(value);
        }

        return result;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(
                    value,
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Malformed URL encoding",
                    exception
            );
        }
    }

    private void requireOnly(
            Map<String, List<String>> query,
            Set<String> allowed
    ) {
        for (String name : query.keySet()) {
            if (!allowed.contains(name)) {
                throw new BadRequestException(
                        "Unknown query parameter: " + name
                );
            }
        }
    }

    private String requireSingle(
            Map<String, List<String>> query,
            String name
    ) {
        List<String> values = query.get(name);

        if (values == null || values.isEmpty()) {
            throw new BadRequestException(
                    "Missing query parameter: " + name
            );
        }

        if (values.size() != 1) {
            throw new BadRequestException(
                    "Query parameter must appear exactly once: " + name
            );
        }

        String value = values.get(0);
        if (value.isBlank()) {
            throw new BadRequestException(
                    "Query parameter cannot be blank: " + name
            );
        }

        return value;
    }

    private Map<String, Object> statusBody(FileStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", status.path());
        body.put("state", status.getStateName());
        body.put("tracked", status.isTracked());
        body.put("safe", status.isSafe());
        return body;
    }

    private void sendError(
            HttpExchange exchange,
            int status,
            String code,
            String message
    ) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message == null ? "" : message);
        sendJson(exchange, status, body);
    }

    private void sendJson(
            HttpExchange exchange,
            int status,
            Object body
    ) throws IOException {
        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(body);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=utf-8"
        );
        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );
        exchange.getResponseHeaders().set(
                "X-Content-Type-Options",
                "nosniff"
        );

        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "FileStatusHttpServer is closed"
            );
        }
    }

    private static final class BadRequestException
            extends IllegalArgumentException {

        private BadRequestException(String message) {
            super(message);
        }

        private BadRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
