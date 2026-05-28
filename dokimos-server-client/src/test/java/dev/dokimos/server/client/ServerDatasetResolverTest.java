package dev.dokimos.server.client;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.dokimos.core.Dataset;
import dev.dokimos.core.DatasetResolutionException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerDatasetResolverTest {

    private HttpServer server;
    private String serverUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RecordedRequest> recordedRequests = new CopyOnWriteArrayList<>();

    // Per-test fixtures wired by individual tests before they fire requests.
    private final Map<String, Integer> latestVersionByDataset = new HashMap<>();
    private final Map<String, Map<Integer, List<Map<String, Object>>>> itemsByDataset = new HashMap<>();
    private final Map<String, AtomicInteger> failuresLeftByPath = new HashMap<>();
    private volatile boolean returnNotFound = false;

    @TempDir
    Path cacheRoot;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        serverUrl = "http://localhost:" + port;
        server.createContext("/", new DatasetHandler());
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // URI parsing

    @Test
    void shouldParsePinnedVersion() {
        ServerDatasetResolver.ParsedUri parsed = ServerDatasetResolver.parse("dataset://foo@3");
        assertThat(parsed.name()).isEqualTo("foo");
        assertThat(parsed.version()).isEqualTo(3);
        assertThat(parsed.isLatest()).isFalse();
    }

    @Test
    void shouldParseLatest() {
        ServerDatasetResolver.ParsedUri parsed = ServerDatasetResolver.parse("dataset://foo@latest");
        assertThat(parsed.name()).isEqualTo("foo");
        assertThat(parsed.isLatest()).isTrue();
    }

    @Test
    void shouldRejectMissingVersion() {
        assertThatThrownBy(() -> ServerDatasetResolver.parse("dataset://foo"))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("version");
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> ServerDatasetResolver.parse("dataset://@3"))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectZeroVersion() {
        assertThatThrownBy(() -> ServerDatasetResolver.parse("dataset://foo@0"))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("positive integer");
    }

    @Test
    void shouldRejectNonNumericVersion() {
        assertThatThrownBy(() -> ServerDatasetResolver.parse("dataset://foo@bar"))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("positive integer");
    }

    // supports()

    @Test
    void supportsReturnsFalseWhenServerUrlMissing() {
        ServerDatasetResolver resolver = new ServerDatasetResolver(null, null, HttpClient.newHttpClient(), cacheRoot);
        assertThat(resolver.supports("dataset://foo@1")).isFalse();
    }

    @Test
    void supportsReturnsFalseForNonDatasetUri() {
        ServerDatasetResolver resolver = newResolver(null);
        assertThat(resolver.supports("file:foo.json")).isFalse();
        assertThat(resolver.supports("classpath:foo.json")).isFalse();
    }

    @Test
    void supportsReturnsTrueWhenServerUrlSet() {
        ServerDatasetResolver resolver = newResolver(null);
        assertThat(resolver.supports("dataset://foo@1")).isTrue();
        assertThat(resolver.supports("dataset://foo@latest")).isTrue();
    }

    // Happy path + pagination

    @Test
    void resolvesSinglePageOfItems() {
        seedVersion("refund-qa", 3, List.of(item(0, "q1", "a1"), item(1, "q2", "a2")));

        Dataset dataset = newResolver(null).resolve("dataset://refund-qa@3");

        assertThat(dataset.name()).isEqualTo("refund-qa@3");
        assertThat(dataset.size()).isEqualTo(2);
        assertThat(dataset.get(0).inputs()).containsEntry("question", "q1");
        assertThat(dataset.get(0).expectedOutputs()).containsEntry("answer", "a1");
        assertThat(dataset.get(0).metadata()).containsEntry("ordinal", 0);
        assertThat(dataset.get(1).inputs()).containsEntry("question", "q2");
    }

    @Test
    void resolvesMultiplePagesInOrder() {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) {
            items.add(item(i, "q" + i, "a" + i));
        }
        seedVersion("big", 1, items);

        Dataset dataset = newResolver(null).resolve("dataset://big@1");

        assertThat(dataset.size()).isEqualTo(400);
        for (int i = 0; i < 400; i++) {
            assertThat(dataset.get(i).inputs()).containsEntry("question", "q" + i);
        }
        long itemRequests =
                recordedRequests.stream().filter(r -> r.path.contains("/items")).count();
        assertThat(itemRequests).isEqualTo(2);
    }

    @Test
    void resolvesLatestAndBakesConcreteVersion() {
        latestVersionByDataset.put("evolving", 7);
        seedVersion("evolving", 7, List.of(item(0, "hi", "hello")));

        Dataset dataset = newResolver(null).resolve("dataset://evolving@latest");

        assertThat(dataset.name()).isEqualTo("evolving@7");
        boolean hitLatest =
                recordedRequests.stream().anyMatch(r -> r.path.equals("/api/v1/datasets/evolving/versions/latest"));
        assertThat(hitLatest).isTrue();
        // Resolving latest writes a pinned cache entry under the concrete version so a follow-up
        // pinned CI run benefits from the cache without another network hop.
        assertThat(cacheRoot.resolve("evolving@7").resolve("items.json")).exists();
    }

    @Test
    void sortsItemsByOrdinalEvenIfServerReturnsOutOfOrder() {
        // Server returns items in a non-monotonic order; the resolver must sort each page by ordinal.
        seedVersion("shuffled", 1, List.of(item(2, "third", "c"), item(0, "first", "a"), item(1, "second", "b")));

        Dataset dataset = newResolver(null).resolve("dataset://shuffled@1");

        assertThat(dataset.size()).isEqualTo(3);
        assertThat(dataset.get(0).inputs()).containsEntry("question", "first");
        assertThat(dataset.get(1).inputs()).containsEntry("question", "second");
        assertThat(dataset.get(2).inputs()).containsEntry("question", "third");
    }

    // Retry + auth

    @Test
    void retriesOn500AndSendsAuthEachTime() {
        seedVersion("refund-qa", 1, List.of(item(0, "q1", "a1")));
        // Fail the items page on the first attempt only.
        failuresLeftByPath
                .computeIfAbsent("/api/v1/datasets/refund-qa/versions/1/items", k -> new AtomicInteger(0))
                .set(1);

        Dataset dataset = newResolver("secret-key").resolve("dataset://refund-qa@1");

        assertThat(dataset.size()).isEqualTo(1);
        List<RecordedRequest> itemAttempts = recordedRequests.stream()
                .filter(r -> r.path.startsWith("/api/v1/datasets/refund-qa/versions/1/items"))
                .toList();
        assertThat(itemAttempts).hasSize(2);
        assertThat(itemAttempts.get(0).authHeader).isEqualTo("Bearer secret-key");
        assertThat(itemAttempts.get(1).authHeader).isEqualTo("Bearer secret-key");
    }

    @Test
    void doesNotRetryOn404() {
        returnNotFound = true;

        assertThatThrownBy(() -> newResolver(null).resolve("dataset://missing@1"))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("404");

        long versionAttempts = recordedRequests.stream()
                .filter(r -> r.path.equals("/api/v1/datasets/missing/versions/1"))
                .count();
        assertThat(versionAttempts).isEqualTo(1);
    }

    // Cache behavior

    @Test
    void writesCacheOnSuccessAndReadsItOnNetworkFailure() throws IOException {
        seedVersion("refund-qa", 2, List.of(item(0, "q1", "a1"), item(1, "q2", "a2")));

        ServerDatasetResolver resolver = newResolver(null);
        Dataset first = resolver.resolve("dataset://refund-qa@2");
        assertThat(first.size()).isEqualTo(2);

        Path cacheFile = cacheRoot.resolve("refund-qa@2").resolve("items.json");
        assertThat(Files.exists(cacheFile)).isTrue();
        JsonNode json = objectMapper.readTree(cacheFile.toFile());
        assertThat(json.get("name").asText()).isEqualTo("refund-qa");
        assertThat(json.get("version").asInt()).isEqualTo(2);
        assertThat(json.get("items")).hasSize(2);

        // Bring the server down and resolve again on the same pinned version.
        server.stop(0);
        server = null;

        Dataset second = resolver.resolve("dataset://refund-qa@2");
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.get(0).inputs()).containsEntry("question", "q1");
    }

    @Test
    void latestDoesNotUseCacheWhenServerDown() {
        // Prime the cache with concrete version 2 via a normal resolve.
        latestVersionByDataset.put("refund-qa", 2);
        seedVersion("refund-qa", 2, List.of(item(0, "q1", "a1")));
        ServerDatasetResolver resolver = newResolver(null);
        resolver.resolve("dataset://refund-qa@latest");

        Path cacheFile = cacheRoot.resolve("refund-qa@2").resolve("items.json");
        assertThat(Files.exists(cacheFile)).isTrue();

        server.stop(0);
        server = null;

        assertThatThrownBy(() -> resolver.resolve("dataset://refund-qa@latest"))
                .isInstanceOf(DatasetResolutionException.class);
    }

    @Test
    void resolveSucceedsWhenCacheDirCannotBeCreated() throws IOException {
        // Use a regular file as the cache root so any attempt to create directories fails.
        Path blocker = Files.createTempFile("dokimos-cache-block", ".tmp");
        blocker.toFile().deleteOnExit();

        seedVersion("refund-qa", 1, List.of(item(0, "q1", "a1")));

        ServerDatasetResolver resolver =
                new ServerDatasetResolver(serverUrl, null, HttpClient.newHttpClient(), blocker);
        Dataset dataset = resolver.resolve("dataset://refund-qa@1");

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(Files.isRegularFile(blocker)).isTrue();
    }

    // helpers

    private ServerDatasetResolver newResolver(String apiKey) {
        return new ServerDatasetResolver(serverUrl, apiKey, HttpClient.newHttpClient(), cacheRoot);
    }

    private void seedVersion(String name, int version, List<Map<String, Object>> items) {
        itemsByDataset.computeIfAbsent(name, k -> new HashMap<>()).put(version, items);
        latestVersionByDataset.putIfAbsent(name, version);
    }

    private Map<String, Object> item(int ordinal, String question, String answer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", UUID.randomUUID().toString());
        m.put("ordinal", ordinal);
        m.put("inputs", Map.of("question", question));
        m.put("expectedOutputs", Map.of("answer", answer));
        m.put("metadata", Map.of("ordinal", ordinal));
        return m;
    }

    private class DatasetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            recordedRequests.add(new RecordedRequest(method, path + (query != null ? "?" + query : ""), authHeader));

            if (returnNotFound) {
                respond(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }

            AtomicInteger fails = failuresLeftByPath.get(path);
            if (fails != null && fails.getAndDecrement() > 0) {
                respond(exchange, 500, "{\"error\":\"boom\"}");
                return;
            }

            // /api/v1/datasets/{name}/versions/{version}[/items]
            String prefix = "/api/v1/datasets/";
            if (!path.startsWith(prefix)) {
                respond(exchange, 404, "{}");
                return;
            }
            String rest = path.substring(prefix.length());
            String[] parts = rest.split("/");
            if (parts.length < 3 || !"versions".equals(parts[1])) {
                respond(exchange, 404, "{}");
                return;
            }
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String versionSegment = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);

            int versionNumber;
            if ("latest".equalsIgnoreCase(versionSegment)) {
                Integer latest = latestVersionByDataset.get(name);
                if (latest == null) {
                    respond(exchange, 404, "{\"error\":\"no versions\"}");
                    return;
                }
                versionNumber = latest;
            } else {
                try {
                    versionNumber = Integer.parseInt(versionSegment);
                } catch (NumberFormatException ex) {
                    respond(exchange, 400, "{\"error\":\"bad version\"}");
                    return;
                }
            }

            Map<Integer, List<Map<String, Object>>> versions = itemsByDataset.get(name);
            if (versions == null || !versions.containsKey(versionNumber)) {
                respond(exchange, 404, "{\"error\":\"unknown version\"}");
                return;
            }
            List<Map<String, Object>> items = versions.get(versionNumber);

            if (parts.length == 3) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", UUID.randomUUID().toString());
                body.put("datasetName", name);
                body.put("version", versionNumber);
                body.put("description", "");
                body.put("itemCount", items.size());
                body.put("createdAt", "2026-01-01T00:00:00Z");
                body.put("createdBy", null);
                respond(exchange, 200, objectMapper.writeValueAsString(body));
                return;
            }
            if (parts.length == 4 && "items".equals(parts[3])) {
                int page = 0;
                int size = 50;
                if (query != null) {
                    for (String p : query.split("&")) {
                        String[] kv = p.split("=");
                        if (kv.length == 2) {
                            if ("page".equals(kv[0])) {
                                page = Integer.parseInt(kv[1]);
                            } else if ("size".equals(kv[0])) {
                                size = Integer.parseInt(kv[1]);
                            }
                        }
                    }
                }
                int from = Math.min(page * size, items.size());
                int to = Math.min(from + size, items.size());
                List<Map<String, Object>> slice = items.subList(from, to);
                int totalPages = (int) Math.ceil((double) items.size() / size);
                if (totalPages == 0) {
                    totalPages = 1;
                }

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("content", slice);
                body.put("number", page);
                body.put("size", size);
                body.put("totalElements", items.size());
                body.put("totalPages", totalPages);
                body.put("first", page == 0);
                body.put("last", page >= totalPages - 1);
                body.put("numberOfElements", slice.size());
                body.put("empty", slice.isEmpty());
                respond(exchange, 200, objectMapper.writeValueAsString(body));
                return;
            }
            respond(exchange, 404, "{}");
        }

        private void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    private record RecordedRequest(String method, String path, String authHeader) {}
}
