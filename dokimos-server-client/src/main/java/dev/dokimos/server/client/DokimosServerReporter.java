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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async HTTP {@link Reporter} that sends experiment results to a Dokimos server.
 * <p>
 * Items are queued and sent in batches of up to 10 items or every 500ms by a background thread.
 * Requests use exponential-backoff retries (3 attempts) and idempotency keys so retried POSTs
 * deduplicate server-side.
 */
public class DokimosServerReporter implements Reporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DokimosServerReporter.class);
    private static final int MAX_BATCH_SIZE = 10;
    private static final long BATCH_TIMEOUT_MS = 500;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 100;
    // Upper bound on an honored Retry-After so a large (or hostile) value cannot park the single
    // worker thread for an unbounded time and stall delivery for every run.
    private static final long MAX_RETRY_AFTER_MS = 60_000;

    /** Default API version when none is specified. */
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
    private final AtomicInteger inFlight;
    private final AtomicInteger failedItems;
    private final ConcurrentHashMap<String, AtomicInteger> pendingByRun;
    private final Object flushLock;
    private final Consumer<ItemDeliveryFailure> onItemDeliveryFailure;
    private final Path spoolDirectory;
    private final Object spoolLock;

    private DokimosServerReporter(Builder builder) {
        this.serverUrl = builder.serverUrl.endsWith("/")
                ? builder.serverUrl.substring(0, builder.serverUrl.length() - 1)
                : builder.serverUrl;
        this.projectName = builder.projectName;
        this.apiVersion = builder.apiVersion != null ? builder.apiVersion : DEFAULT_API_VERSION;
        this.apiKey = builder.apiKey;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        this.objectMapper = new ObjectMapper();
        this.queue = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(true);
        this.pendingItems = new AtomicInteger(0);
        this.inFlight = new AtomicInteger(0);
        this.failedItems = new AtomicInteger(0);
        this.pendingByRun = new ConcurrentHashMap<>();
        this.flushLock = new Object();
        this.onItemDeliveryFailure = builder.onItemDeliveryFailure;
        this.spoolDirectory = builder.spoolDirectory;
        this.spoolLock = new Object();

        this.workerThread = new Thread(this::processQueue, "dokimos-reporter-worker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    /** New builder for {@link DokimosServerReporter}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a reporter from environment variables: {@code DOKIMOS_SERVER_URL} and
     * {@code DOKIMOS_PROJECT_NAME} are required; {@code DOKIMOS_API_KEY} and
     * {@code DOKIMOS_API_VERSION} are optional.
     *
     * @throws IllegalStateException if a required variable is missing
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

        Builder builder = builder().serverUrl(serverUrl).projectName(projectName);

        if (apiVersion != null && !apiVersion.isBlank()) {
            builder.apiVersion(apiVersion);
        }

        if (apiKey != null && !apiKey.isBlank()) {
            builder.apiKey(apiKey);
        }

        return builder.build();
    }

    /** Package-private for tests. */
    String getApiVersion() {
        return apiVersion;
    }

    /** Package-private for tests. Items queued or in flight. */
    int pendingItemCount() {
        return pendingItems.get();
    }

    /** Package-private for tests. Item POSTs currently in flight. */
    int inFlightCount() {
        return inFlight.get();
    }

    /**
     * Number of items permanently dropped after exhausting all delivery retries.
     *
     * @return the count of items that could not be delivered to the server
     */
    public int getFailedItemCount() {
        return failedItems.get();
    }

    @Override
    public RunHandle startRun(String experimentName, Map<String, Object> metadata) {
        String url = serverUrl + "/api/" + apiVersion + "/projects/" + projectName + "/runs";

        Map<String, Object> body = Map.of(
                "experimentName", experimentName,
                "metadata", metadata);

        String response = executeWithRetry("POST", url, body);
        if (response == null) {
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
        // Increment counters before enqueueing so an item is never queued but uncounted. The
        // per-run counter is decremented only after the send completes, covering queued, batched,
        // and in-flight states so completeRun cannot finalize while a batch is mid-flight.
        pendingItems.incrementAndGet();
        pendingByRun.computeIfAbsent(handle.runId(), k -> new AtomicInteger(0)).incrementAndGet();
        queue.offer(new QueuedItem(handle, result));
    }

    @Override
    public void completeRun(RunHandle handle, RunStatus status) {
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
        // Block until both the queue is drained and no send is in flight.
        long deadline = System.currentTimeMillis() + 30000;
        while ((pendingItems.get() > 0 || inFlight.get() > 0) && System.currentTimeMillis() < deadline) {
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

                boolean shouldSend = !batch.isEmpty()
                        && (batch.size() >= MAX_BATCH_SIZE
                                || (item == null && !batch.isEmpty())
                                || (System.currentTimeMillis() - batchStartTime) >= BATCH_TIMEOUT_MS);

                if (shouldSend) {
                    sendBatch(batch);
                    batch.clear();
                    batchStartTime = 0;
                }

            } catch (InterruptedException e) {
                Thread.interrupted();

                QueuedItem item;
                while ((item = queue.poll()) != null) {
                    batch.add(item);
                }

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

        Map<String, List<ItemResult>> itemsByRun = new java.util.HashMap<>();
        for (QueuedItem item : batch) {
            itemsByRun
                    .computeIfAbsent(item.handle.runId(), k -> new ArrayList<>())
                    .add(item.result);
        }

        for (Map.Entry<String, List<ItemResult>> entry : itemsByRun.entrySet()) {
            String runId = entry.getKey();
            List<ItemResult> items = entry.getValue();

            String url = serverUrl + "/api/" + apiVersion + "/runs/" + runId + "/items";
            List<Map<String, Object>> itemsPayload =
                    items.stream().map(this::itemResultToMap).toList();

            Map<String, Object> body = Map.of("items", itemsPayload);

            // One idempotency key per run POST, reused across retries so a successful retry of an
            // already-recorded request deduplicates server-side.
            Map<String, String> headers =
                    Map.of("Idempotency-Key", UUID.randomUUID().toString());

            // Counters are decremented in finally on every path so flush() cannot hang. Permanent-failure
            // accounting (count, spool, callback) runs before the finally so it is complete by the time
            // flush() observes inFlight == 0; the callback contract forbids re-entrant flush()/close().
            inFlight.incrementAndGet();
            try {
                String response = executeWithRetry("POST", url, body, headers);
                if (response != null) {
                    LOGGER.debug("Sent batch of {} items to run {}", items.size(), runId);
                } else {
                    handlePermanentFailure(runId, items);
                }
            } finally {
                inFlight.decrementAndGet();
                pendingItems.addAndGet(-items.size());
                // Zeroed entries are left in pendingByRun; race-free removal would need extra
                // coordination with reportItem and the per-reporter run count is small.
                AtomicInteger runPending = pendingByRun.get(runId);
                if (runPending != null) {
                    runPending.addAndGet(-items.size());
                }
                synchronized (flushLock) {
                    flushLock.notifyAll();
                }
            }
        }
    }

    private void handlePermanentFailure(String runId, List<ItemResult> items) {
        LOGGER.error(
                "Failed to deliver {} items for run {} after {} retries; these items were NOT recorded",
                items.size(),
                runId,
                MAX_RETRIES);
        failedItems.addAndGet(items.size());

        if (spoolDirectory != null) {
            spoolFailedBatch(runId, items);
        }

        if (onItemDeliveryFailure != null) {
            try {
                onItemDeliveryFailure.accept(new ItemDeliveryFailure(runId, items.size(), List.copyOf(items)));
            } catch (RuntimeException e) {
                LOGGER.warn("onItemDeliveryFailure callback threw for run {}: {}", runId, e.getMessage());
            }
        }
    }

    private void spoolFailedBatch(String runId, List<ItemResult> items) {
        // Append one JSON object per line ({"runId":..,"items":[..]}); a JSON-lines (NDJSON) file
        // a future replay step can read back line by line. Automatic replay is out of scope here.
        try {
            Files.createDirectories(spoolDirectory);
            Path spoolFile = spoolDirectory.resolve("failed-items.ndjson");

            List<Map<String, Object>> itemsPayload =
                    items.stream().map(this::itemResultToMap).toList();
            Map<String, Object> line = Map.of("runId", runId, "items", itemsPayload);
            String json = objectMapper.writeValueAsString(line) + System.lineSeparator();

            synchronized (spoolLock) {
                Files.writeString(
                        spoolFile, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to spool {} undelivered items for run {}: {}", items.size(), runId, e.getMessage());
        }
    }

    private Map<String, Object> itemResultToMap(ItemResult result) {
        var map = new java.util.HashMap<String, Object>();
        map.put("inputs", result.example().inputs());
        map.put("expectedOutputs", result.example().expectedOutputs());
        map.put("actualOutputs", result.actualOutputs());
        map.put(
                "evalResults",
                result.evalResults().stream().map(this::evalResultToMap).toList());
        map.put("success", result.success());
        Map<String, Object> exampleMetadata = result.example().metadata();
        if (exampleMetadata != null && !exampleMetadata.isEmpty()) {
            map.put("metadata", exampleMetadata);
        }
        String datasetItemId = result.example().datasetItemId();
        if (datasetItemId != null) {
            map.put("datasetItemId", datasetItemId);
        }
        dev.dokimos.core.CallMetrics metrics = result.metrics();
        if (metrics != null) {
            if (metrics.tokensIn() != null) {
                map.put("tokensIn", metrics.tokensIn());
            }
            if (metrics.tokensOut() != null) {
                map.put("tokensOut", metrics.tokensOut());
            }
            if (metrics.costUsd() != null) {
                map.put("costUsd", metrics.costUsd());
            }
            if (metrics.latencyMs() != null) {
                map.put("latencyMs", metrics.latencyMs());
            }
        }
        return map;
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
        // Block until pendingByRun for this run is zero. The counter spans the entire lifecycle
        // (queued, batched, in flight) so completeRun cannot finalize while items are mid-flight.
        String runId = handle.runId();
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            AtomicInteger runPending = pendingByRun.get(runId);
            if (runPending == null || runPending.get() <= 0) {
                return;
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
        return executeWithRetry(method, url, body, Map.of());
    }

    private String executeWithRetry(String method, String url, Map<String, Object> body, Map<String, String> headers) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;
        // When set by a Retry-After header, overrides the exponential backoff for the next wait.
        long retryAfterMs = -1;

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

                for (Map.Entry<String, String> header : headers.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }

                HttpRequest request =
                        switch (method) {
                            case "POST" ->
                                requestBuilder
                                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                        .build();
                            case "PATCH" ->
                                requestBuilder
                                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                                        .build();
                            default -> throw new IllegalArgumentException("Unsupported method: " + method);
                        };

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }

                // 429 (Too Many Requests) is transient and retryable; prefer the Retry-After hint
                // for the next wait when present. Other 4xx remain terminal.
                if (response.statusCode() == 429) {
                    retryAfterMs = parseRetryAfterMs(
                            response.headers().firstValue("Retry-After").orElse(null));
                    LOGGER.debug("Rate limited (429), attempt {} of {}", attempt, MAX_RETRIES);
                } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    LOGGER.warn("Client error {} for {} {}", response.statusCode(), method, url);
                    return null;
                } else {
                    LOGGER.debug("Server error {}, attempt {} of {}", response.statusCode(), attempt, MAX_RETRIES);
                }

            } catch (IOException | InterruptedException e) {
                LOGGER.debug("Request failed, attempt {} of {}: {}", attempt, MAX_RETRIES, e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(retryAfterMs >= 0 ? retryAfterMs : backoff);
                    backoff *= 2;
                    retryAfterMs = -1;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        LOGGER.warn("Failed after {} attempts for {} {}", MAX_RETRIES, method, url);
        return null;
    }

    /**
     * Parses a Retry-After header (RFC 7231 delay-seconds or HTTP-date) into milliseconds, clamped to
     * {@link #MAX_RETRY_AFTER_MS}. Returns -1 when the header is absent or unparseable.
     */
    private static long parseRetryAfterMs(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return -1;
        }
        String trimmed = headerValue.trim();
        long millis;
        try {
            long seconds = Long.parseLong(trimmed);
            if (seconds <= 0) {
                return 0;
            }
            // Overflow-safe: cap before multiplying so a huge value cannot wrap negative.
            millis = seconds > MAX_RETRY_AFTER_MS / 1000 ? MAX_RETRY_AFTER_MS : seconds * 1000;
        } catch (NumberFormatException notSeconds) {
            try {
                long until = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                                .toInstant()
                                .toEpochMilli()
                        - System.currentTimeMillis();
                millis = Math.max(0, until);
            } catch (DateTimeParseException notDate) {
                return -1;
            }
        }
        return Math.min(millis, MAX_RETRY_AFTER_MS);
    }

    private record QueuedItem(RunHandle handle, ItemResult result) {}

    /**
     * Describes a batch of items permanently dropped after all delivery retries were exhausted.
     *
     * @param runId the run the items belonged to
     * @param itemCount the number of items in the dropped batch
     * @param items the dropped item results
     */
    public record ItemDeliveryFailure(String runId, int itemCount, List<ItemResult> items) {
        public ItemDeliveryFailure {
            items = List.copyOf(items);
        }
    }

    /** Builder for {@link DokimosServerReporter}. */
    public static class Builder {
        private String serverUrl;
        private String projectName;
        private String apiVersion;
        private String apiKey;
        private HttpClient httpClient;
        private Consumer<ItemDeliveryFailure> onItemDeliveryFailure;
        private Path spoolDirectory;

        private Builder() {}

        /** Dokimos server URL, e.g. {@code "https://api.my-domain.com"}. */
        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        /** Project name on the server. */
        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        /** API version, e.g. {@code "v1"}. Defaults to {@link DokimosServerReporter#DEFAULT_API_VERSION}. */
        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /** Bearer API key for authentication. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Package-private: inject a custom HTTP client for tests. */
        Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Callback invoked when a batch of items is permanently dropped after all delivery retries.
         * The callback runs on the reporter's background worker thread, so keep it lightweight and do
         * NOT call {@code flush()}, {@code close()}, or {@code reportItem(...)} on this reporter from it;
         * doing so re-enters the worker and stalls delivery. Exceptions thrown by the callback are
         * caught and logged.
         */
        public Builder onItemDeliveryFailure(Consumer<ItemDeliveryFailure> onItemDeliveryFailure) {
            this.onItemDeliveryFailure = onItemDeliveryFailure;
            return this;
        }

        /**
         * Opt-in durable spool directory. When set, permanently-failed item batches are appended as
         * JSON lines to {@code failed-items.ndjson} in this directory so a transient outage past all
         * retries does not silently lose data. Defaults to {@code null} (disabled).
         */
        public Builder spoolDirectory(Path spoolDirectory) {
            this.spoolDirectory = spoolDirectory;
            return this;
        }

        /** @throws IllegalStateException if {@code serverUrl} or {@code projectName} is not set. */
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
