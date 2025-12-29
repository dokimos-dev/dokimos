package dev.dokimos.server.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.Reporter;
import dev.dokimos.core.RunHandle;
import dev.dokimos.core.RunStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An async HTTP implementation of {@link Reporter} that sends experiment
 * results to a Dokimos server.
 * <p>
 * Items are queued and sent in batches by a background thread to reduce HTTP
 * overhead.
 * Batches are sent when either 10 items are queued or 500ms have passed since
 * the first item in the batch.
 */
public class DokimosServerReporter implements Reporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DokimosServerReporter.class);
    private static final int MAX_BATCH_SIZE = 10;
    private static final long BATCH_TIMEOUT_MS = 500;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 100;

    /**
     * The default API version used when not explicitly specified.
     */
    public static final String DEFAULT_API_VERSION = "v1";

    private final String serverUrl;
    private final String projectName;
    private final String apiVersion;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<QueuedItem> queue;
    private final Thread workerThread;
    private final AtomicBoolean running;
    private final AtomicInteger pendingItems;
    private final Object flushLock;

    private DokimosServerReporter(Builder builder) {
        this.serverUrl = builder.serverUrl.endsWith("/")
                ? builder.serverUrl.substring(0, builder.serverUrl.length() - 1)
                : builder.serverUrl;
        this.projectName = builder.projectName;
        this.apiVersion = builder.apiVersion != null ? builder.apiVersion : DEFAULT_API_VERSION;
        this.apiKey = builder.apiKey;
        this.httpClient = builder.httpClient != null ? builder.httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

        this.objectMapper = new ObjectMapper();
        this.queue = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(true);
        this.pendingItems = new AtomicInteger(0);
        this.flushLock = new Object();

        this.workerThread = new Thread(this::processQueue, "dokimos-reporter-worker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    /**
     * Creates a new builder for {@link DokimosServerReporter}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a reporter from environment variables.
     * <p>
     * Reads {@code DOKIMOS_SERVER_URL} and {@code DOKIMOS_PROJECT_NAME} from the
     * environment. Optionally reads {@code DOKIMOS_API_KEY} for authentication
     * and {@code DOKIMOS_API_VERSION} for version pinning.
     *
     * @return a configured reporter
     * @throws IllegalStateException if required environment variables are not set
     */
    public static DokimosServerReporter fromEnvironment() {
        String serverUrl = System.getenv("DOKIMOS_SERVER_URL");
        String projectName = System.getenv("DOKIMOS_PROJECT_NAME");
        String apiKey = System.getenv("DOKIMOS_API_KEY");
        String apiVersion = System.getenv("DOKIMOS_API_VERSION");

        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalStateException("DOKIMOS_SERVER_URL environment variable is not set");
        }
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalStateException("DOKIMOS_PROJECT_NAME environment variable is not set");
        }

        Builder builder = builder()
                .serverUrl(serverUrl)
                .projectName(projectName);

        if (apiVersion != null && !apiVersion.isBlank()) {
            builder.apiVersion(apiVersion);
        }

        if (apiKey != null && !apiKey.isBlank()) {
            builder.apiKey(apiKey);
        }

        return builder.build();
    }

    /**
     * Returns the API version being used.
     * Package-private for testing.
     */
    String getApiVersion() {
        return apiVersion;
    }

    @Override
    public RunHandle startRun(String experimentName, Map<String, Object> metadata) {
        String url = serverUrl + "/api/" + apiVersion + "/projects/" + projectName + "/runs";

        Map<String, Object> body = Map.of(
                "experimentName", experimentName,
                "metadata", metadata);

        String response = executeWithRetry("POST", url, body);
        if (response == null) {
            // Generate a local run ID if server is unavailable
            String localId = "local-" + System.currentTimeMillis();
            LOGGER.warn("Failed to start run on server, using local ID: {}", localId);
            return new RunHandle(localId);
        }

        try {
            JsonNode json = objectMapper.readTree(response);
            String runId = json.get("runId").asText();
            LOGGER.debug("Started run {} for experiment '{}'", runId, experimentName);
            return new RunHandle(runId);
        } catch (JsonProcessingException e) {
            String localId = "local-" + System.currentTimeMillis();
            LOGGER.warn("Failed to parse run response, using local ID: {}", localId);
            return new RunHandle(localId);
        }
    }

    @Override
    public void reportItem(RunHandle handle, ItemResult result) {
        pendingItems.incrementAndGet();
        queue.offer(new QueuedItem(handle, result));
    }

    @Override
    public void completeRun(RunHandle handle, RunStatus status) {
        // Make sure all items are sent before completing
        flushItemsForRun(handle);

        String url = serverUrl + "/api/" + apiVersion + "/runs/" + handle.runId();
        Map<String, Object> body = Map.of("status", status.name());

        String response = executeWithRetry("PATCH", url, body);
        if (response != null) {
            LOGGER.debug("Completed run {} with status {}", handle.runId(), status);
        }
    }

    @Override
    public void flush() {
        // Wait until the queue is empty and all pending items are processed
        long deadline = System.currentTimeMillis() + 30000;
        while (pendingItems.get() > 0 && System.currentTimeMillis() < deadline) {
            synchronized (flushLock) {
                try {
                    flushLock.wait(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Flush interrupted");
                    return;
                }
            }
        }
    }

    @Override
    public void close() {
        flush();
        running.set(false);
        workerThread.interrupt();
        try {
            workerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void processQueue() {
        List<QueuedItem> batch = new ArrayList<>();
        long batchStartTime = 0;

        while (running.get() || !queue.isEmpty() || !batch.isEmpty()) {
            try {
                long timeout;
                if (batch.isEmpty()) {
                    timeout = BATCH_TIMEOUT_MS;
                } else {
                    timeout = Math.max(1, BATCH_TIMEOUT_MS - (System.currentTimeMillis() - batchStartTime));
                }

                QueuedItem item = queue.poll(timeout, TimeUnit.MILLISECONDS);

                if (item != null) {
                    if (batch.isEmpty()) {
                        batchStartTime = System.currentTimeMillis();
                    }
                    batch.add(item);
                }

                boolean shouldSend = !batch.isEmpty() && (batch.size() >= MAX_BATCH_SIZE ||
                        (item == null && !batch.isEmpty()) ||
                        (System.currentTimeMillis() - batchStartTime) >= BATCH_TIMEOUT_MS);

                if (shouldSend) {
                    sendBatch(batch);
                    batch.clear();
                    batchStartTime = 0;
                }

            } catch (InterruptedException e) {
                // Clear interrupt flag
                Thread.interrupted();

                // Drain remaining items from queue
                QueuedItem item;
                while ((item = queue.poll()) != null) {
                    batch.add(item);
                }

                // Send whatever we have
                if (!batch.isEmpty()) {
                    sendBatch(batch);
                    batch.clear();
                }

                if (!running.get()) {
                    break;
                }
            }
        }
    }

    private void sendBatch(List<QueuedItem> batch) {
        if (batch.isEmpty()) {
            return;
        }

        int itemCount = batch.size();

        // Group items by run handle
        Map<String, List<ItemResult>> itemsByRun = new java.util.HashMap<>();
        for (QueuedItem item : batch) {
            itemsByRun.computeIfAbsent(item.handle.runId(), k -> new ArrayList<>())
                    .add(item.result);
        }

        for (Map.Entry<String, List<ItemResult>> entry : itemsByRun.entrySet()) {
            String runId = entry.getKey();
            List<ItemResult> items = entry.getValue();

            String url = serverUrl + "/api/" + apiVersion + "/runs/" + runId + "/items";
            List<Map<String, Object>> itemsPayload = items.stream()
                    .map(this::itemResultToMap)
                    .toList();

            Map<String, Object> body = Map.of("items", itemsPayload);

            String response = executeWithRetry("POST", url, body);
            if (response != null) {
                LOGGER.debug("Sent batch of {} items to run {}", items.size(), runId);
            }
        }

        // Decrement pending count and notify flush waiters
        pendingItems.addAndGet(-itemCount);
        synchronized (flushLock) {
            flushLock.notifyAll();
        }
    }

    private Map<String, Object> itemResultToMap(ItemResult result) {
        return Map.of(
                "inputs", result.example().inputs(),
                "expectedOutputs", result.example().expectedOutputs(),
                "actualOutputs", result.actualOutputs(),
                "evalResults", result.evalResults().stream()
                        .map(this::evalResultToMap)
                        .toList(),
                "success", result.success());
    }

    private Map<String, Object> evalResultToMap(dev.dokimos.core.EvalResult er) {
        var map = new java.util.HashMap<String, Object>();
        map.put("name", er.name());
        map.put("score", er.score());
        map.put("success", er.success());
        map.put("reason", er.reason() != null ? er.reason() : "");
        map.put("metadata", er.metadata());
        if (er.threshold() != null) {
            map.put("threshold", er.threshold());
        }
        return map;
    }

    private void flushItemsForRun(RunHandle handle) {
        // Wait until all items for this specific run are processed
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            boolean hasItemsForRun = queue.stream()
                    .anyMatch(item -> item.handle().equals(handle));
            if (!hasItemsForRun) {
                // Give a short time for any in-flight batch containing this run to complete
                synchronized (flushLock) {
                    try {
                        flushLock.wait(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                // Check again after the wait
                hasItemsForRun = queue.stream()
                        .anyMatch(item -> item.handle().equals(handle));
                if (!hasItemsForRun) {
                    return;
                }
            }
            synchronized (flushLock) {
                try {
                    flushLock.wait(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private String executeWithRetry(String method, String url, Map<String, Object> body) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                String jsonBody = objectMapper.writeValueAsString(body);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30));

                if (apiKey != null) {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                }

                HttpRequest request = switch (method) {
                    case "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
                    case "PATCH" ->
                        requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build();
                    default -> throw new IllegalArgumentException("Unsupported method: " + method);
                };

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }

                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    // Client error, don't retry
                    LOGGER.warn("Client error {} for {} {}", response.statusCode(), method, url);
                    return null;
                }

                // Server error, retry
                LOGGER.debug("Server error {}, attempt {} of {}", response.statusCode(), attempt, MAX_RETRIES);

            } catch (IOException | InterruptedException e) {
                LOGGER.debug("Request failed, attempt {} of {}: {}", attempt, MAX_RETRIES, e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        LOGGER.warn("Failed after {} attempts for {} {}", MAX_RETRIES, method, url);
        return null;
    }

    private record QueuedItem(RunHandle handle, ItemResult result) {
    }

    /**
     * Builder for {@link DokimosServerReporter}.
     */
    public static class Builder {
        private String serverUrl;
        private String projectName;
        private String apiVersion;
        private String apiKey;
        private HttpClient httpClient;

        private Builder() {
        }

        /**
         * Sets the Dokimos server URL.
         *
         * @param serverUrl the server URL, e.g. "https://api.my-domain.com"
         * @return this builder
         */
        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        /**
         * Sets the project name.
         *
         * @param projectName the project name
         * @return this builder
         */
        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        /**
         * Sets the API version to use.
         * <p>
         * If not specified, defaults to {@link DokimosServerReporter#DEFAULT_API_VERSION}.
         *
         * @param apiVersion the API version, e.g. "v1" or "v2"
         * @return this builder
         */
        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /**
         * Sets the API key for authentication.
         *
         * @param apiKey the API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets a custom HTTP client (useful for testing).
         *
         * @param httpClient the HTTP client to use
         * @return this builder
         */
        Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Builds the reporter.
         *
         * @return a new {@link DokimosServerReporter}
         * @throws IllegalStateException if serverUrl or projectName is not set
         */
        public DokimosServerReporter build() {
            if (serverUrl == null || serverUrl.isBlank()) {
                throw new IllegalStateException("serverUrl is required");
            }
            if (projectName == null || projectName.isBlank()) {
                throw new IllegalStateException("projectName is required");
            }
            return new DokimosServerReporter(this);
        }
    }
}
