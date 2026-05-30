package dev.dokimos.server.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenAiCompatibleJudgeTest {

    @Test
    @SuppressWarnings("unchecked")
    void appendsChatCompletionsToTheConfiguredBaseUrl() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":1.0}\"}}]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OpenAiCompatibleJudge judge =
                new OpenAiCompatibleJudge(client, "https://api.openai.com/v1", "gpt-4o-mini", "test-key");
        judge.generate("score this");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    @SuppressWarnings("unchecked")
    void trimsTrailingSlashOnTheBaseUrl() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":1.0}\"}}]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OpenAiCompatibleJudge judge =
                new OpenAiCompatibleJudge(client, "http://localhost:11434/v1/", "llama3", "test-key");
        judge.generate("score this");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo("http://localhost:11434/v1/chat/completions");
    }
}
