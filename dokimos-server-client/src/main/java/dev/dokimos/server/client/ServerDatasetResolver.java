package dev.dokimos.server.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.Dataset;
import dev.dokimos.core.DatasetResolutionException;
import dev.dokimos.core.DatasetResolver;
import dev.dokimos.core.Example;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves dataset URIs of the form {@code dataset://<name>@<version>} against a Dokimos server.
 * <p>
 * Pinned versions (numeric) are fetched network-first with a local fallback cached under
 * {@code ~/.dokimos/datasets-cache/<name>@<version>/items.json}. The {@code latest} alias always
 * goes to the network so newly published versions are picked up; once it resolves to a concrete
 * version a pinned cache entry is written.
 * <p>
 * Discovered via {@link java.util.ServiceLoader}; the no-arg constructor reads
 * {@code DOKIMOS_SERVER_URL} and {@code DOKIMOS_API_KEY} from the environment at
 * {@link #supports(String)} time, so a JVM that never sets the variable keeps the resolver inert
 * and lets the next resolver in the chain handle the URI.
 */
public class ServerDatasetResolver implements DatasetResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDatasetResolver.class);

    /** URI scheme handled by this resolver. */
    public static final String SCHEME = "dataset://";

    /** Version path alias that resolves to the newest version on the server. */
    public static final String LATEST = "latest";

    /** Page size used when listing dataset items. */
    public static final int PAGE_SIZE = 200;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 100;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String API_PATH = "/api/v1/datasets/";

    private final String serverUrlOverride;
    private final String apiKeyOverride;
    private final boolean useEnvironment;
    private final HttpClient httpClient;
    private final Path cacheRoot;
    private final ObjectMapper objectMapper;

    /**
     * Creates a resolver wired to {@code DOKIMOS_SERVER_URL} / {@code DOKIMOS_API_KEY}.
     * Used by {@link java.util.ServiceLoader} when discovering resolvers on the classpath.
     */
    public ServerDatasetResolver() {
        this.serverUrlOverride = null;
        this.apiKeyOverride = null;
        this.useEnvironment = true;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.cacheRoot = defaultCacheRoot();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Package-private constructor for tests.
     * <p>
     * When {@code serverUrl} is {@code null} the resolver behaves as if {@code DOKIMOS_SERVER_URL}
     * were unset (so {@link #supports(String)} returns {@code false}).
     *
     * @param serverUrl  the server base URL, or {@code null} to simulate unset env
     * @param apiKey     bearer key, or {@code null} for unauthenticated requests
     * @param httpClient HTTP client to use; must not be {@code null}
     * @param cacheRoot  directory used for the offline cache; must not be {@code null}
     */
    ServerDatasetResolver(String serverUrl, String apiKey, HttpClient httpClient, Path cacheRoot) {
        this.serverUrlOverride = serverUrl;
        this.apiKeyOverride = apiKey;
        this.useEnvironment = false;
        this.httpClient = httpClient;
        this.cacheRoot = cacheRoot;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Indicates whether this resolver will attempt the given URI.
     * <p>
     * Returns {@code false} for non {@code dataset://} URIs and when no server URL is configured,
     * so the registry can fall through to file/classpath resolvers. Malformed URIs are accepted
     * here and rejected in {@link #resolve(String)} with a precise error message.
     */
    @Override
    public boolean supports(String uri) {
        // Any dataset:// URI is ours; missing config surfaces as a clear error from resolve()
        // instead of a generic "no resolver found" from the registry.
        return uri != null && uri.startsWith(SCHEME);
    }

    /**
     * Fetches the dataset at the URI from the server, applying retries on 5xx responses and
     * falling back to the offline cache when a pinned version is unreachable.
     *
     * @throws DatasetResolutionException if the URI is malformed, the server returns a 4xx,
     *                                    or all retries fail with no usable cache entry
     */
    @Override
    public Dataset resolve(String uri) {
        ParsedUri parsed = parse(uri);
        String serverUrl = currentServerUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new DatasetResolutionException("DOKIMOS_SERVER_URL is not configured; cannot resolve " + uri);
        }
        String base = stripTrailingSlash(serverUrl);
        String apiKey = currentApiKey();

        if (parsed.isLatest()) {
            return resolveLatest(parsed, base, apiKey, uri);
        }
        return resolvePinned(parsed, parsed.version(), base, apiKey, uri);
    }

    private Dataset resolveLatest(ParsedUri parsed, String base, String apiKey, String uri) {
        try {
            int concreteVersion = fetchVersionNumber(parsed.name(), LATEST, base, apiKey);
            return resolvePinned(parsed, concreteVersion, base, apiKey, uri);
        } catch (DatasetResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new DatasetResolutionException("Failed to resolve latest version of " + parsed.name(), e);
        }
    }

    private Dataset resolvePinned(ParsedUri parsed, int version, String base, String apiKey, String uri) {
        Exception networkError = null;
        try {
            int confirmed = fetchVersionNumber(parsed.name(), Integer.toString(version), base, apiKey);
            List<Example> examples = fetchAllItems(parsed.name(), confirmed, base, apiKey);
            writeCache(parsed.name(), confirmed, examples);
            return buildDataset(parsed.name(), confirmed, examples);
        } catch (DatasetResolutionException e) {
            // 4xx and parse errors are surfaced directly; cache fallback is for transient outages.
            throw e;
        } catch (Exception e) {
            networkError = e;
            LOGGER.debug("Network fetch failed for {}@{}: {}", parsed.name(), version, e.getMessage());
        }

        List<Example> cached = readCache(parsed.name(), version);
        if (cached != null) {
            LOGGER.info("Using cached dataset {}@{} after network failure", parsed.name(), version);
            return buildDataset(parsed.name(), version, cached);
        }
        throw new DatasetResolutionException(
                "Failed to resolve " + uri + " and no cached copy is available", networkError);
    }

    private int fetchVersionNumber(String name, String versionSegment, String base, String apiKey) throws IOException {
        String url = base + API_PATH + encode(name) + "/versions/" + encode(versionSegment);
        String body = sendWithRetry(url, apiKey);
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode versionNode = node.get("version");
            if (versionNode == null || !versionNode.isInt()) {
                throw new DatasetResolutionException("Version response missing 'version' field: " + url);
            }
            return versionNode.asInt();
        } catch (IOException e) {
            throw new DatasetResolutionException("Failed to parse version response from " + url, e);
        }
    }

    private List<Example> fetchAllItems(String name, int version, String base, String apiKey) throws IOException {
        List<RawItem> all = new ArrayList<>();
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            String url = base + API_PATH + encode(name) + "/versions/" + version + "/items?page=" + page + "&size="
                    + PAGE_SIZE;
            String body = sendWithRetry(url, apiKey);
            JsonNode root;
            try {
                root = objectMapper.readTree(body);
            } catch (IOException e) {
                throw new DatasetResolutionException("Failed to parse items response from " + url, e);
            }
            JsonNode totalPagesNode = root.get("totalPages");
            if (totalPagesNode != null && totalPagesNode.isInt()) {
                totalPages = Math.max(1, totalPagesNode.asInt());
            }
            JsonNode content = root.get("content");
            if (content == null || !content.isArray()) {
                throw new DatasetResolutionException("Items response missing 'content' array: " + url);
            }
            for (JsonNode item : content) {
                all.add(toRawItem(item));
            }
            page++;
            if (content.size() == 0) {
                break;
            }
        }
        // Sort across all pages so the result is ordinal-ordered even if the server's pagination
        // ever stops returning pages in ordinal order.
        all.sort(Comparator.comparingInt(r -> r.ordinal));
        List<Example> collected = new ArrayList<>(all.size());
        for (RawItem r : all) {
            collected.add(new Example(r.inputs, r.expectedOutputs, r.metadata));
        }
        return collected;
    }

    private RawItem toRawItem(JsonNode item) {
        int ordinal = item.has("ordinal") ? item.get("ordinal").asInt() : 0;
        Map<String, Object> inputs = readMap(item.get("inputs"));
        Map<String, Object> expected = readMap(item.get("expectedOutputs"));
        Map<String, Object> metadata = readMap(item.get("metadata"));
        return new RawItem(ordinal, inputs, expected, metadata);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (IllegalArgumentException e) {
            return Map.of();
        }
    }

    private String sendWithRetry(String url, String apiKey) throws IOException {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;
        IOException lastIo = null;
        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .GET();
                if (apiKey != null && !apiKey.isBlank()) {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                }
                HttpResponse<String> response =
                        httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response.body();
                }
                if (status >= 400 && status < 500) {
                    throw new DatasetResolutionException(
                            "Server returned " + status + " for " + url + ": " + response.body());
                }
                LOGGER.debug("Server error {} for {}, attempt {} of {}", status, url, attempt, MAX_RETRIES);
            } catch (DatasetResolutionException e) {
                throw e;
            } catch (IOException e) {
                lastIo = e;
                LOGGER.debug("Request to {} failed, attempt {} of {}: {}", url, attempt, MAX_RETRIES, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + url, e);
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while backing off for " + url, e);
                }
            }
        }
        throw new IOException(
                "Exhausted retries for " + url, lastIo != null ? lastIo : new IOException("Server returned 5xx"));
    }

    private Dataset buildDataset(String name, int version, List<Example> examples) {
        String displayName = name + "@" + version;
        return new Dataset(displayName, "", examples);
    }

    private Path cacheFile(String name, int version) {
        if (cacheRoot == null) {
            return null;
        }
        return cacheRoot.resolve(name + "@" + version).resolve("items.json");
    }

    private void writeCache(String name, int version, List<Example> examples) {
        Path target = cacheFile(name, version);
        if (target == null) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            LOGGER.warn("Could not create cache directory {}: {}", target.getParent(), e.getMessage());
            return;
        }

        List<Map<String, Object>> serialized = new ArrayList<>(examples.size());
        for (Example example : examples) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("inputs", example.inputs());
            entry.put("expectedOutputs", example.expectedOutputs());
            entry.put("metadata", example.metadata());
            serialized.add(entry);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", name);
        root.put("version", version);
        root.put("items", serialized);

        // Per-write unique temp name so concurrent writers cannot trample each other's temp file on
        // the non-atomic fallback path (different filesystems may reject ATOMIC_MOVE).
        Path tmp = target.resolveSibling(target.getFileName().toString() + "." + UUID.randomUUID() + ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Could not write dataset cache to {}: {}", target, e.getMessage());
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    private List<Example> readCache(String name, int version) {
        Path target = cacheFile(name, version);
        if (target == null || !Files.isRegularFile(target)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(target.toFile());
            JsonNode items = root.get("items");
            if (items == null || !items.isArray()) {
                return null;
            }
            List<Example> examples = new ArrayList<>(items.size());
            for (JsonNode item : items) {
                Map<String, Object> inputs = objectMapper.convertValue(item.get("inputs"), new TypeReference<>() {});
                Map<String, Object> expected =
                        objectMapper.convertValue(item.get("expectedOutputs"), new TypeReference<>() {});
                Map<String, Object> metadata =
                        objectMapper.convertValue(item.get("metadata"), new TypeReference<>() {});
                examples.add(new Example(
                        inputs != null ? inputs : Map.of(),
                        expected != null ? expected : Map.of(),
                        metadata != null ? metadata : Map.of()));
            }
            return examples;
        } catch (IOException e) {
            LOGGER.warn("Could not read dataset cache at {}: {}", target, e.getMessage());
            return null;
        }
    }

    private String currentServerUrl() {
        if (useEnvironment) {
            return System.getenv("DOKIMOS_SERVER_URL");
        }
        return serverUrlOverride;
    }

    private String currentApiKey() {
        if (useEnvironment) {
            return System.getenv("DOKIMOS_API_KEY");
        }
        return apiKeyOverride;
    }

    private static Path defaultCacheRoot() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return null;
        }
        return Paths.get(home, ".dokimos", "datasets-cache");
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static ParsedUri parse(String uri) {
        if (uri == null || !uri.startsWith(SCHEME)) {
            throw new DatasetResolutionException("Not a dataset URI: " + uri);
        }
        String body = uri.substring(SCHEME.length());
        int at = body.indexOf('@');
        if (at < 0) {
            throw new DatasetResolutionException(
                    "Dataset URI must include a version, e.g. dataset://name@1 or dataset://name@latest: " + uri);
        }
        String name = body.substring(0, at);
        String versionPart = body.substring(at + 1);
        if (name.isBlank()) {
            throw new DatasetResolutionException("Dataset URI is missing a name: " + uri);
        }
        if (versionPart.isBlank()) {
            throw new DatasetResolutionException("Dataset URI is missing a version: " + uri);
        }
        if (LATEST.equalsIgnoreCase(versionPart)) {
            return new ParsedUri(name, 0, true);
        }
        int parsedVersion;
        try {
            parsedVersion = Integer.parseInt(versionPart);
        } catch (NumberFormatException ex) {
            throw new DatasetResolutionException("Dataset URI version must be a positive integer or 'latest': " + uri);
        }
        if (parsedVersion <= 0) {
            throw new DatasetResolutionException("Dataset URI version must be a positive integer or 'latest': " + uri);
        }
        return new ParsedUri(name, parsedVersion, false);
    }

    /** Parsed components of a {@code dataset://name@version} URI. */
    record ParsedUri(String name, int version, boolean isLatest) {}

    private record RawItem(
            int ordinal,
            Map<String, Object> inputs,
            Map<String, Object> expectedOutputs,
            Map<String, Object> metadata) {}
}
