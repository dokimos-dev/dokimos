package dev.dokimos.server.client;

import static org.assertj.core.api.Assertions.*;

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
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DokimosServerReporterTest {

    private HttpServer server;
    private String serverUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RecordedRequest> recordedRequests = new CopyOnWriteArrayList<>();

    // Item POST response control for the recording handler.
    private volatile boolean alwaysFailItems = false;
    private volatile boolean failFirstItemPost = false;
    private volatile long itemSendDelayMillis = 0;
    private final java.util.concurrent.atomic.AtomicInteger itemPostAttempts =
            new java.util.concurrent.atomic.AtomicInteger(0);

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

            // flush() blocks until every item has been sent and recorded, so no extra wait is needed.
            reporter.flush();

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
                    example, Map.of("output", "4"), List.of(EvalResult.success("exact-match", 1.0, "Correct")));

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

    @Test
    void shouldSendDatasetItemIdWhenPresentAndOmitWhenNull() throws Exception {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            Example linked = new Example(Map.of("input", "q"), Map.of("output", "a"), Map.of(), "item-abc");
            Example unlinked = Example.of("q2", "a2");
            reporter.reportItem(
                    handle, new ItemResult(linked, Map.of("output", "a"), List.of(EvalResult.success("e", 1.0, "ok"))));
            reporter.reportItem(
                    handle,
                    new ItemResult(unlinked, Map.of("output", "a2"), List.of(EvalResult.success("e", 1.0, "ok"))));
            reporter.flush();

            JsonNode items = collectItems();
            assertThat(items).hasSize(2);

            JsonNode withId = findItemByInput(items, "q");
            assertThat(withId.has("datasetItemId")).isTrue();
            assertThat(withId.get("datasetItemId").asText()).isEqualTo("item-abc");

            JsonNode withoutId = findItemByInput(items, "q2");
            assertThat(withoutId.has("datasetItemId")).isFalse();
        }
    }

    @Test
    void shouldSendCallMetricsWhenPresentAndOmitWhenNull() throws Exception {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            ItemResult withMetrics = ItemResult.builder(
                            Example.of("q-metrics", "a"),
                            Map.of("output", "a"),
                            List.of(EvalResult.success("e", 1.0, "ok")))
                    .tokensIn(100)
                    .tokensOut(50)
                    .costUsd(0.002)
                    .latencyMs(430L)
                    .build();
            ItemResult withoutMetrics = new ItemResult(
                    Example.of("q-bare", "a"), Map.of("output", "a"), List.of(EvalResult.success("e", 1.0, "ok")));

            reporter.reportItem(handle, withMetrics);
            reporter.reportItem(handle, withoutMetrics);
            reporter.flush();

            JsonNode items = collectItems();
            assertThat(items).hasSize(2);

            JsonNode metricsItem = findItemByInput(items, "q-metrics");
            assertThat(metricsItem.get("tokensIn").asInt()).isEqualTo(100);
            assertThat(metricsItem.get("tokensOut").asInt()).isEqualTo(50);
            assertThat(metricsItem.get("costUsd").asDouble()).isEqualTo(0.002);
            assertThat(metricsItem.get("latencyMs").asLong()).isEqualTo(430L);

            JsonNode bareItem = findItemByInput(items, "q-bare");
            assertThat(bareItem.has("tokensIn")).isFalse();
            assertThat(bareItem.has("tokensOut")).isFalse();
            assertThat(bareItem.has("costUsd")).isFalse();
            assertThat(bareItem.has("latencyMs")).isFalse();
        }
    }

    private JsonNode collectItems() throws Exception {
        List<RecordedRequest> itemRequests =
                recordedRequests.stream().filter(r -> r.path.contains("/items")).toList();
        com.fasterxml.jackson.databind.node.ArrayNode all = objectMapper.createArrayNode();
        for (RecordedRequest r : itemRequests) {
            for (JsonNode item : objectMapper.readTree(r.body).get("items")) {
                all.add(item);
            }
        }
        return all;
    }

    private JsonNode findItemByInput(JsonNode items, String input) {
        for (JsonNode item : items) {
            if (input.equals(item.path("inputs").path("input").asText())) {
                return item;
            }
        }
        throw new AssertionError("No item with input " + input);
    }

    @Test
    void shouldSendIdempotencyKeyOnItemPosts() {
        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            reporter.reportItem(handle, createItemResult("q1", "a1"));
            reporter.flush();

            List<RecordedRequest> itemRequests = recordedRequests.stream()
                    .filter(r -> r.path.contains("/items"))
                    .toList();

            assertThat(itemRequests).hasSize(1);
            assertThat(itemRequests.get(0).idempotencyKey).isNotBlank();
        }
    }

    @Test
    void shouldReuseSameIdempotencyKeyAcrossRetryAttempts() {
        // First item POST returns 500, the retry returns 201. Both attempts must carry the same key.
        failFirstItemPost = true;

        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            reporter.reportItem(handle, createItemResult("q1", "a1"));
            reporter.flush();

            List<RecordedRequest> itemRequests = recordedRequests.stream()
                    .filter(r -> r.path.contains("/items"))
                    .toList();

            // The same logical POST was attempted twice (500 then 201).
            assertThat(itemRequests).hasSize(2);
            assertThat(itemRequests.get(0).idempotencyKey).isNotBlank();
            assertThat(itemRequests.get(1).idempotencyKey).isEqualTo(itemRequests.get(0).idempotencyKey);
        }
    }

    @Test
    void shouldDrainPendingAndTerminateFlushWhenBatchPermanentlyFails() {
        // Every item POST returns 500, so the batch fails after all retries.
        alwaysFailItems = true;

        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            for (int i = 0; i < 5; i++) {
                reporter.reportItem(handle, createItemResult("q" + i, "a" + i));
            }

            long start = System.currentTimeMillis();
            reporter.flush();
            long elapsed = System.currentTimeMillis() - start;

            // flush() must terminate well before its 30s deadline even though delivery failed.
            // It should finish in roughly 2s (retries plus backoff); a tighter bound catches a
            // regression where flush hangs to the full 30s deadline.
            assertThat(elapsed).isLessThan(5000);
            // Pending counter returns to 0 so a subsequent flush cannot hang forever.
            assertThat(reporter.pendingItemCount()).isZero();
        }
    }

    @Test
    void shouldWaitForInFlightSendBeforeFlushReturns() throws Exception {
        // The handler delays each item POST so the send is still in flight when flush() is called.
        itemSendDelayMillis = 300;

        try (var reporter = createReporter()) {
            RunHandle handle = reporter.startRun("test", Map.of());
            recordedRequests.clear();

            reporter.reportItem(handle, createItemResult("q1", "a1"));

            // Wait until the item has left the queue and the send is in flight.
            long deadline = System.currentTimeMillis() + 2000;
            while (reporter.inFlightCount() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertThat(reporter.inFlightCount()).isGreaterThan(0);

            reporter.flush();

            // After flush returns, the in-flight send must have completed and been recorded.
            assertThat(reporter.inFlightCount()).isZero();
            List<RecordedRequest> itemRequests = recordedRequests.stream()
                    .filter(r -> r.path.contains("/items"))
                    .toList();
            assertThat(itemRequests).hasSize(1);
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
                example, Map.of("output", expectedOutput), List.of(EvalResult.success("test-eval", 1.0, "pass")));
    }

    private class RecordingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");

            recordedRequests.add(new RecordedRequest(method, path, body, authHeader, idempotencyKey));

            String response;
            int statusCode = 200;

            if (path.endsWith("/runs") && method.equals("POST")) {
                response = "{\"runId\": \"test-run-123\"}";
                statusCode = 201;
            } else if (path.contains("/items")) {
                statusCode = itemStatusFor();
                response = statusCode >= 200 && statusCode < 300 ? "{\"status\": \"ok\"}" : "{\"error\": \"boom\"}";
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

        private int itemStatusFor() {
            if (itemSendDelayMillis > 0) {
                try {
                    Thread.sleep(itemSendDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (alwaysFailItems) {
                return 500;
            }
            if (failFirstItemPost && itemPostAttempts.getAndIncrement() == 0) {
                return 500;
            }
            return 201;
        }
    }

    private record RecordedRequest(String method, String path, String body, String authHeader, String idempotencyKey) {}
}
