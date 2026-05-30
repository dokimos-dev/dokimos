package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.dokimos.server.dto.v1.RegressionAlertPayload;
import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.AlertWebhookRepository;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertWebhookDispatcherTest {

    @Mock
    private AlertWebhookRepository webhookRepository;

    @Test
    void signShouldMatchKnownHmacSha256() {
        // Reference value for HMAC-SHA256 of "hello" with key "key" (lowercase hex).
        String expected = "9307b3b915efb5171ff14d8cb55fbcc798c6c0ef1456d66ded1a6aa723a58b7b";
        assertThat(AlertWebhookDispatcher.sign("hello", "key")).isEqualTo(expected);
    }

    @Test
    void shouldPostSignedBodyToEnabledWebhook() throws Exception {
        UUID projectId = UUID.randomUUID();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                receivedBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            receivedSignature.set(exchange.getRequestHeaders().getFirst(AlertWebhookDispatcher.SIGNATURE_HEADER));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            AlertWebhook webhook = new AlertWebhook(project(projectId), url, "topsecret", true);
            when(webhookRepository.findByProjectIdAndEnabledTrue(projectId)).thenReturn(List.of(webhook));

            ObjectMapper mapper = new ObjectMapper();
            AlertWebhookDispatcher dispatcher =
                    new AlertWebhookDispatcher(webhookRepository, mapper, HttpClient.newHttpClient());

            RegressionAlertPayload payload = new RegressionAlertPayload(
                    "acme", UUID.randomUUID(), "qa", UUID.randomUUID(), UUID.randomUUID(), 0.9, 0.6, -0.3, 4);
            dispatcher.onRegressionAlert(new RegressionAlertEvent(projectId, payload));

            assertThat(receivedBody.get()).isNotNull();
            String expectedBody = mapper.writeValueAsString(payload);
            assertThat(receivedBody.get()).isEqualTo(expectedBody);
            assertThat(receivedSignature.get()).isEqualTo(AlertWebhookDispatcher.sign(expectedBody, "topsecret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotSignWhenNoSecret() throws Exception {
        UUID projectId = UUID.randomUUID();
        AtomicReference<String> receivedSignature = new AtomicReference<>("present");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            receivedSignature.set(exchange.getRequestHeaders().getFirst(AlertWebhookDispatcher.SIGNATURE_HEADER));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            AlertWebhook webhook = new AlertWebhook(project(projectId), url, null, true);
            when(webhookRepository.findByProjectIdAndEnabledTrue(projectId)).thenReturn(List.of(webhook));

            AlertWebhookDispatcher dispatcher =
                    new AlertWebhookDispatcher(webhookRepository, new ObjectMapper(), HttpClient.newHttpClient());

            dispatcher.onRegressionAlert(new RegressionAlertEvent(projectId, payload()));

            assertThat(receivedSignature.get()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldSwallowDeliveryFailure() {
        UUID projectId = UUID.randomUUID();
        // Unroutable URL: connection fails, but the dispatcher must not throw.
        AlertWebhook webhook = new AlertWebhook(project(projectId), "http://127.0.0.1:1/hook", "s", true);
        when(webhookRepository.findByProjectIdAndEnabledTrue(projectId)).thenReturn(List.of(webhook));

        AlertWebhookDispatcher dispatcher =
                new AlertWebhookDispatcher(webhookRepository, new ObjectMapper(), HttpClient.newHttpClient());

        dispatcher.onRegressionAlert(new RegressionAlertEvent(projectId, payload()));
    }

    private RegressionAlertPayload payload() {
        return new RegressionAlertPayload(
                "acme", UUID.randomUUID(), "qa", UUID.randomUUID(), UUID.randomUUID(), 0.9, 0.6, -0.3, 4);
    }

    private Project project(UUID id) {
        Project project = new Project("p-" + id);
        setField(project, "id", id);
        return project;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
