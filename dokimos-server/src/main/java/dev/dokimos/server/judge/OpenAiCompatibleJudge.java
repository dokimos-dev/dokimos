package dev.dokimos.server.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dokimos.core.JudgeLM;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A {@link JudgeLM} that calls an OpenAI-compatible chat completions endpoint over the JDK HTTP
 * client, with no vendor SDK. The payload is built and parsed with Jackson. A failed call (HTTP
 * status 400 or above, a timeout, or a network error) raises a {@link JudgeCallException} carrying the
 * status so the worker can decide whether to retry. The API key is held only for the lifetime of the
 * job and is never logged.
 */
public class OpenAiCompatibleJudge implements JudgeLM {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String model;
    private final String apiKey;

    public OpenAiCompatibleJudge(String baseUrl, String model, String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), baseUrl, model, apiKey);
    }

    OpenAiCompatibleJudge(HttpClient httpClient, String baseUrl, String model, String apiKey) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public String generate(String prompt) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(buildPayload(prompt)))
                    .build();
        } catch (Exception e) {
            throw new JudgeCallException(JudgeCallException.NETWORK_ERROR, "Failed to build judge request", e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JudgeCallException(JudgeCallException.NETWORK_ERROR, "Judge call interrupted", e);
        } catch (Exception e) {
            throw new JudgeCallException(JudgeCallException.NETWORK_ERROR, "Judge call failed", e);
        }

        if (response.statusCode() >= 400) {
            throw new JudgeCallException(
                    response.statusCode(), "Judge endpoint returned HTTP " + response.statusCode());
        }

        return extractContent(response.body());
    }

    private String endpoint() {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + "/v1/chat/completions";
    }

    private String buildPayload(String prompt) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", model);
        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new JudgeCallException(JudgeCallException.NETWORK_ERROR, "Failed to serialize judge payload", e);
        }
    }

    private String extractContent(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new JudgeCallException(JudgeCallException.NETWORK_ERROR, "Judge response has no message content");
            }
            return content.asText();
        } catch (JudgeCallException e) {
            throw e;
        } catch (Exception e) {
            throw new JudgeCallException(JudgeCallException.NETWORK_ERROR, "Failed to read judge response", e);
        }
    }
}
