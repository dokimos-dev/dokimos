package dev.dokimos.springai;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.AsyncTask;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.Example;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.TaskResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Verifies {@link SpringAiSupport#measuredAsyncTask} produces the {@link CallMetrics} that feed all
 * three run-detail cards: tokens from {@code ChatResponse} usage, latency around the call, and cost
 * via a supplied {@link PriceTable}.
 */
class SpringAiMeasuredTaskTest {

    private static final PriceTable PRICES = (model, in, out) -> {
        if (!"<test-model>".equals(model) || in == null || out == null) {
            return null;
        }
        return (in * 0.5 + out * 1.5) / 1_000_000d;
    };

    private static ChatClient clientReturning(ChatResponse response) {
        ChatModel model = prompt -> response;
        return ChatClient.builder(model).build();
    }

    private static ChatResponse withUsage(String text, Integer in, Integer out) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(in, out, in + out))
                        .model("<test-model>")
                        .build());
    }

    @Test
    void measuredAsyncTask_withUsageAndPriceTable_populatesAllThreeCardValues() throws Exception {
        ChatClient client = clientReturning(withUsage("Paris", 10, 20));

        AsyncTask task = SpringAiSupport.measuredAsyncTask(client, "<test-model>", PRICES);
        TaskResult result = task.run(Example.of("capital of France?", "Paris")).get();

        assertThat(result.outputs()).containsEntry("output", "Paris");
        CallMetrics metrics = result.metrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.tokensIn()).isEqualTo(10);
        assertThat(metrics.tokensOut()).isEqualTo(20);
        assertThat(metrics.costUsd()).isEqualTo((10 * 0.5 + 20 * 1.5) / 1_000_000d);
        assertThat(metrics.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    void measuredAsyncTask_withUsageButNoPriceTable_lightsTokensAndLatencyNotCost() throws Exception {
        ChatClient client = clientReturning(withUsage("hi", 7, 3));

        AsyncTask task = SpringAiSupport.measuredAsyncTask(client, "<test-model>", null);
        CallMetrics metrics = task.run(Example.of("q", "a")).get().metrics();

        assertThat(metrics.tokensIn()).isEqualTo(7);
        assertThat(metrics.tokensOut()).isEqualTo(3);
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isNotNull();
    }

    @Test
    void measuredAsyncTask_withUnknownModel_leavesCostNullButKeepsTokens() throws Exception {
        ChatClient client = clientReturning(withUsage("hi", 7, 3));

        AsyncTask task = SpringAiSupport.measuredAsyncTask(client, "model-not-in-table", PRICES);
        CallMetrics metrics = task.run(Example.of("q", "a")).get().metrics();

        assertThat(metrics.tokensIn()).isEqualTo(7);
        assertThat(metrics.costUsd()).isNull();
    }

    @Test
    void measuredAsyncTask_withoutUsage_doesNotThrowAndLeavesTokensNull() throws Exception {
        ChatClient client = clientReturning(new ChatResponse(List.of(new Generation(new AssistantMessage("hi")))));

        AsyncTask task = SpringAiSupport.measuredAsyncTask(client, "<test-model>", PRICES);
        CallMetrics metrics = task.run(Example.of("q", "a")).get().metrics();

        assertThat(metrics.tokensIn()).isNull();
        assertThat(metrics.tokensOut()).isNull();
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isNotNull();
    }

    @Test
    void measuredAsyncTask_nullClient_throws() {
        assertThatThrownBy(() -> SpringAiSupport.measuredAsyncTask(null, "<test-model>", PRICES))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
