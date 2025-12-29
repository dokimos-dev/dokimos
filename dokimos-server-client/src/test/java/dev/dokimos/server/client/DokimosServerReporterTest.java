package dev.dokimos.server.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunHandle;
import dev.dokimos.core.RunStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

class DokimosServerReporterTest {

    private HttpServer server;
    private String serverUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RecordedRequest> recordedRequests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        serverUrl = "http://localhost:" + port;

        server.createContext("/", new RecordingHandler());
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldRequireServerUrl() {
        assertThatThrownBy(() -> DokimosServerReporter.builder()
                .projectName("test-project")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serverUrl");
    }

    @Test
    void shouldRequireProjectName() {
        assertThatThrownBy(() -> DokimosServerReporter.builder()
                .serverUrl("http://localhost")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projectName");
    }

    @Test
    void shouldStartRunAndReturnHandle() {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test-experiment", Map.of("key", "value"));

            assertThat(handle).isNotNull();
            assertThat(handle.runId()).isEqualTo("test-run-123");

            assertThat(recordedRequests).hasSize(1);
            RecordedRequest request = recordedRequests.get(0);
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.path).isEqualTo("/api/v1/projects/my-project/runs");
            assertThat(request.body).contains("test-experiment");
        }
    }

    @Test
    void shouldQueueItemsAndSendInBatches() throws Exception {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            // Report 15 items - should result in 2 batches (10 + 5)
            for (int i = 0; i < 15; i++) {
                reporter.reportItem(handle, createItemResult("q" + i, "a" + i));
            }

            reporter.flush();

            // Give time for all HTTP requests to be recorded
            Thread.sleep(100);

            // Filter for item requests only
            List<RecordedRequest> itemRequests = recordedRequests.stream()
                    .filter(r -> r.path.contains("/items"))
                    .toList();

            assertThat(itemRequests).hasSizeGreaterThanOrEqualTo(1);

            // Verify total items sent
            int totalItems = 0;
            for (RecordedRequest request : itemRequests) {
                JsonNode body = objectMapper.readTree(request.body);
                totalItems += body.get("items").size();
            }
            assertThat(totalItems).isEqualTo(15);
        }
    }

    @Test
    void shouldSendBatchAfterTimeout() throws InterruptedException {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            // Report just 2 items (less than batch size)
            reporter.reportItem(handle, createItemResult("q1", "a1"));
            reporter.reportItem(handle, createItemResult("q2", "a2"));

            // Wait for batch timeout (500ms + buffer)
            Thread.sleep(700);

            reporter.flush();

            List<RecordedRequest> itemRequests = recordedRequests.stream()
                    .filter(r -> r.path.contains("/items"))
                    .toList();

            assertThat(itemRequests).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void shouldCompleteRunWithStatus() {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            reporter.completeRun(handle, RunStatus.SUCCESS);

            List<RecordedRequest> patchRequests = recordedRequests.stream()
                    .filter(r -> r.method.equals("PATCH"))
                    .toList();

            assertThat(patchRequests).hasSize(1);
            assertThat(patchRequests.get(0).path).isEqualTo("/api/v1/runs/test-run-123");
            assertThat(patchRequests.get(0).body).contains("SUCCESS");
        }
    }

    @Test
    void shouldFlushBlockUntilQueueEmpty() throws Exception {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            // Report items
            for (int i = 0; i < 5; i++) {
                reporter.reportItem(handle, createItemResult("q" + i, "a" + i));
            }

            // Flush should block until all items are sent
            reporter.flush();

            // After flush, queue should be empty and all items sent
            List<RecordedRequest> itemRequests = recordedRequests.stream()
                    .filter(r -> r.path.contains("/items"))
                    .toList();

            int totalItems = 0;
            for (RecordedRequest request : itemRequests) {
                JsonNode body = objectMapper.readTree(request.body);
                totalItems += body.get("items").size();
            }
            assertThat(totalItems).isEqualTo(5);
        }
    }

    @Test
    void shouldIncludeAuthorizationHeader() {
        try (var reporter = DokimosServerReporter.builder()
                .serverUrl(serverUrl)
                .projectName("my-project")
                .apiKey("secret-key")
                .build()) {

            reporter.startRun("test", Map.of());

            assertThat(recordedRequests).hasSize(1);
            assertThat(recordedRequests.get(0).authHeader).isEqualTo("Bearer secret-key");
        }
    }

    @Test
    void shouldHandleServerErrorGracefully() {
        // Stop the server to simulate connection failure
        server.stop(0);

        try (var reporter = DokimosServerReporter.builder()
                .serverUrl(serverUrl)
                .projectName("my-project")
                .build()) {

            // Should not throw, just return a local run ID
            RunHandle handle = reporter.startRun("test", Map.of());
            assertThat(handle.runId()).startsWith("local-");
        }
    }

    @Test
    void shouldUseDefaultApiVersionV1() {
        try (var reporter = createReporter()) {
            assertThat(reporter.getApiVersion()).isEqualTo("v1");
            assertThat(DokimosServerReporter.DEFAULT_API_VERSION).isEqualTo("v1");
        }
    }

    @Test
    void shouldUseCustomApiVersion() {
        try (var reporter = DokimosServerReporter.builder()
                .serverUrl(serverUrl)
                .projectName("my-project")
                .apiVersion("v2")
                .build()) {

            assertThat(reporter.getApiVersion()).isEqualTo("v2");

            reporter.startRun("test-experiment", Map.of());

            assertThat(recordedRequests).hasSize(1);
            RecordedRequest request = recordedRequests.get(0);
            assertThat(request.path).isEqualTo("/api/v2/projects/my-project/runs");
        }
    }

    @Test
    void shouldUseCustomApiVersionForAllEndpoints() {
        try (var reporter = DokimosServerReporter.builder()
                .serverUrl(serverUrl)
                .projectName("my-project")
                .apiVersion("v2")
                .build()) {

            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            reporter.reportItem(handle, createItemResult("q1", "a1"));
            reporter.flush();

            reporter.completeRun(handle, RunStatus.SUCCESS);

            // Check items endpoint uses v2
            List<RecordedRequest> itemRequests = recordedRequests.stream()
                    .filter(r -> r.path.contains("/items"))
                    .toList();
            assertThat(itemRequests).hasSize(1);
            assertThat(itemRequests.get(0).path).isEqualTo("/api/v2/runs/test-run-123/items");

            // Check PATCH endpoint uses v2
            List<RecordedRequest> patchRequests = recordedRequests.stream()
                    .filter(r -> r.method.equals("PATCH"))
                    .toList();
            assertThat(patchRequests).hasSize(1);
            assertThat(patchRequests.get(0).path).isEqualTo("/api/v2/runs/test-run-123");
        }
    }

    @Test
    void shouldSendItemResultWithEvalResults() throws Exception {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            Example example = Example.of("What is 2+2?", "4");
            ItemResult result = new ItemResult(
                    example,
                    Map.of("output", "4"),
                    List.of(EvalResult.success("exact-match", 1.0, "Correct")));

            reporter.reportItem(handle, result);
            reporter.flush();

            List<RecordedRequest> itemRequests = recordedRequests.stream()
                    .filter(r -> r.path.contains("/items"))
                    .toList();

            assertThat(itemRequests).hasSize(1);
            JsonNode body = objectMapper.readTree(itemRequests.get(0).body);
            JsonNode items = body.get("items");
            assertThat(items).hasSize(1);

            JsonNode item = items.get(0);
            assertThat(item.get("success").asBoolean()).isTrue();
            assertThat(item.get("evalResults")).hasSize(1);
            assertThat(item.get("evalResults").get(0).get("name").asText()).isEqualTo("exact-match");
        }
    }

    private DokimosServerReporter createReporter() {
        return DokimosServerReporter.builder()
                .serverUrl(serverUrl)
                .projectName("my-project")
                .build();
    }

    private ItemResult createItemResult(String input, String expectedOutput) {
        Example example = Example.of(input, expectedOutput);
        return new ItemResult(
                example,
                Map.of("output", expectedOutput),
                List.of(EvalResult.success("test-eval", 1.0, "pass")));
    }

    private class RecordingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

            recordedRequests.add(new RecordedRequest(method, path, body, authHeader));

            String response;
            int statusCode = 200;

            if (path.endsWith("/runs") && method.equals("POST")) {
                response = "{\"runId\": \"test-run-123\"}";
                statusCode = 201;
            } else if (path.contains("/items")) {
                response = "{\"status\": \"ok\"}";
            } else if (method.equals("PATCH")) {
                response = "{\"status\": \"completed\"}";
            } else {
                response = "{}";
            }

            exchange.sendResponseHeaders(statusCode, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private record RecordedRequest(String method, String path, String body, String authHeader) {
    }
}
