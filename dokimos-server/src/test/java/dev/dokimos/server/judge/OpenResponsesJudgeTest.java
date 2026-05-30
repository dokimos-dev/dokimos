package dev.dokimos.server.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenResponsesJudgeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void postsToResponsesEndpointWithAMessageInput() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"output_text\":\"ok\"}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OpenResponsesJudge judge =
                new OpenResponsesJudge(client, "https://api.openai.com/v1", "gpt-4o-mini", "test-key");
        judge.generate("score this");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));

        assertThat(request.getValue().uri().toString()).isEqualTo("https://api.openai.com/v1/responses");

        JsonNode payload = MAPPER.readTree(bodyOf(request.getValue()));
        assertThat(payload.path("model").asText()).isEqualTo("gpt-4o-mini");
        JsonNode message = payload.path("input").path(0);
        assertThat(message.path("type").asText()).isEqualTo("message");
        assertThat(message.path("role").asText()).isEqualTo("user");
        JsonNode part = message.path("content").path(0);
        assertThat(part.path("type").asText()).isEqualTo("input_text");
        assertThat(part.path("text").asText()).isEqualTo("score this");
    }

    @Test
    @SuppressWarnings("unchecked")
    void trimsTrailingSlashOnTheBaseUrl() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"output_text\":\"ok\"}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OpenResponsesJudge judge = new OpenResponsesJudge(client, "http://localhost:8000/v1/", "local", "test-key");
        judge.generate("score this");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo("http://localhost:8000/v1/responses");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsTheTopLevelOutputTextWhenPresent() throws Exception {
        OpenResponsesJudge judge = judgeReturning("{\"output_text\":\"the verdict\"}");
        assertThat(judge.generate("x")).isEqualTo("the verdict");
    }

    @Test
    @SuppressWarnings("unchecked")
    void concatenatesOutputTextPartsWhenNoConvenienceField() throws Exception {
        String body = "{\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":\"part-a \"},"
                + "{\"type\":\"output_text\",\"text\":\"part-b\"}]}]}";
        OpenResponsesJudge judge = judgeReturning(body);
        assertThat(judge.generate("x")).isEqualTo("part-a part-b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void raisesNonRetryableOn4xxAndRetryableOn5xx() throws Exception {
        assertThatThrownBy(() -> judgeWithStatus(400).generate("x"))
                .isInstanceOfSatisfying(JudgeCallException.class, e -> assertThat(e.isRetryable())
                        .isFalse());
        assertThatThrownBy(() -> judgeWithStatus(503).generate("x"))
                .isInstanceOfSatisfying(JudgeCallException.class, e -> assertThat(e.isRetryable())
                        .isTrue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void raisesWhenResponseHasNoOutputText() throws Exception {
        OpenResponsesJudge judge = judgeReturning("{\"output\":[]}");
        assertThatThrownBy(() -> judge.generate("x")).isInstanceOf(JudgeCallException.class);
    }

    @SuppressWarnings("unchecked")
    private static OpenResponsesJudge judgeReturning(String body) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        return new OpenResponsesJudge(client, "https://api.openai.com/v1", "gpt-4o-mini", "test-key");
    }

    @SuppressWarnings("unchecked")
    private static OpenResponsesJudge judgeWithStatus(int status) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        return new OpenResponsesJudge(client, "https://api.openai.com/v1", "gpt-4o-mini", "test-key");
    }

    private static String bodyOf(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        CompletableFuture<String> done = new CompletableFuture<>();
        StringBuilder collected = new StringBuilder();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                collected.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(collected.toString());
            }
        });
        return done.get();
    }
}
