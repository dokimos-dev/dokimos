package dev.dokimos.springai.alibaba;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.core.AsyncTask;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.Example;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.TaskResult;
import dev.dokimos.springai.SpringAiSupport;
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport.AlibabaAgentResponse;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link SpringAiAlibabaSupport#measuredAsyncTask} produces the {@link CallMetrics} that
 * feed the run-detail cards. Because a Spring AI Alibaba graph/agent run does not expose token usage
 * on its result (only a bare {@code AssistantMessage}/message list), the caller supplies token counts
 * via {@link AlibabaAgentResponse}; latency is captured automatically and cost is composed via a
 * supplied {@link PriceTable}.
 */
class SpringAiAlibabaMeasuredTaskTest {

    private static final PriceTable PRICES = (model, in, out) -> {
        if (!"<test-model>".equals(model) || in == null || out == null) {
            return null;
        }
        return (in * 0.5 + out * 1.5) / 1_000_000d;
    };

    @Test
    void measuredAsyncTask_withTokensAndPriceTable_populatesAllThreeCardValues() throws Exception {
        AsyncTask task = SpringAiAlibabaSupport.measuredAsyncTask(
                example -> new AlibabaAgentResponse("Paris", 10, 20), "<test-model>", PRICES);

        TaskResult result = task.run(Example.of("capital of France?", "Paris")).get();

        assertThat(result.outputs()).containsEntry(SpringAiSupport.OUTPUT_KEY, "Paris");
        CallMetrics metrics = result.metrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.tokensIn()).isEqualTo(10);
        assertThat(metrics.tokensOut()).isEqualTo(20);
        assertThat(metrics.costUsd()).isEqualTo((10 * 0.5 + 20 * 1.5) / 1_000_000d);
        assertThat(metrics.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    void measuredAsyncTask_withTokensButNoPriceTable_lightsTokensAndLatencyNotCost() throws Exception {
        AsyncTask task = SpringAiAlibabaSupport.measuredAsyncTask(
                example -> new AlibabaAgentResponse("hi", 7, 3), "<test-model>", null);

        CallMetrics metrics = task.run(Example.of("q", "a")).get().metrics();

        assertThat(metrics.tokensIn()).isEqualTo(7);
        assertThat(metrics.tokensOut()).isEqualTo(3);
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isNotNull();
    }

    @Test
    void measuredAsyncTask_withUnknownModel_leavesCostNullButKeepsTokens() throws Exception {
        AsyncTask task = SpringAiAlibabaSupport.measuredAsyncTask(
                example -> new AlibabaAgentResponse("hi", 7, 3), "model-not-in-table", PRICES);

        CallMetrics metrics = task.run(Example.of("q", "a")).get().metrics();

        assertThat(metrics.tokensIn()).isEqualTo(7);
        assertThat(metrics.costUsd()).isNull();
    }

    @Test
    void measuredAsyncTask_withoutTokens_doesNotThrowAndLeavesTokensNull() throws Exception {
        AsyncTask task = SpringAiAlibabaSupport.measuredAsyncTask(
                example -> AlibabaAgentResponse.of("hi"), "<test-model>", PRICES);

        CallMetrics metrics = task.run(Example.of("q", "a")).get().metrics();

        assertThat(metrics.tokensIn()).isNull();
        assertThat(metrics.tokensOut()).isNull();
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isNotNull();
    }

    @Test
    void measuredAsyncTask_nullResponse_storesEmptyOutputAndNullTokens() throws Exception {
        AsyncTask task = SpringAiAlibabaSupport.measuredAsyncTask(example -> null, "<test-model>", PRICES);

        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(result.outputs()).containsEntry(SpringAiSupport.OUTPUT_KEY, "");
        CallMetrics metrics = result.metrics();
        assertThat(metrics.tokensIn()).isNull();
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isNotNull();
    }

    @Test
    void measuredAsyncTask_withExecutor_runsOnThatExecutor() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            AsyncTask task = SpringAiAlibabaSupport.measuredAsyncTask(
                    example -> new AlibabaAgentResponse("ok", 1, 2), "<test-model>", PRICES, executor);

            CallMetrics metrics = task.run(Example.of("q", "a")).get().metrics();

            assertThat(metrics.tokensIn()).isEqualTo(1);
            assertThat(metrics.tokensOut()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void measuredAsyncTask_nullAgentCall_throws() {
        assertThatThrownBy(() -> SpringAiAlibabaSupport.measuredAsyncTask(null, "<test-model>", PRICES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void measuredAsyncTask_nullExecutor_throws() {
        assertThatThrownBy(() -> SpringAiAlibabaSupport.measuredAsyncTask(
                        example -> AlibabaAgentResponse.of("x"), "<test-model>", PRICES, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
