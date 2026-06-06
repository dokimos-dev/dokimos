package dev.dokimos.langchain4j;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;
import dev.dokimos.core.Experiment;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.MeasuredTask;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.TaskResult;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link LangChain4jSupport#measuredTask} produces the {@link CallMetrics} that feed all
 * three run-detail cards (Total Tokens, Total Cost, Avg Latency): tokens come from the response usage,
 * latency is measured around the call, and cost is composed via a supplied {@link PriceTable}.
 */
class LangChain4jMeasuredTaskTest {

    /** A toy price table: $0.50/M input, $1.50/M output; null for unknown model or missing counts. */
    private static final PriceTable PRICES = (model, in, out) -> {
        if (!"<test-model>".equals(model) || in == null || out == null) {
            return null;
        }
        return (in * 0.5 + out * 1.5) / 1_000_000d;
    };

    private static ChatModel modelReturning(ChatResponse response) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return response;
            }
        };
    }

    @Test
    void measuredTask_withUsageAndPriceTable_populatesAllThreeCardValues() {
        ChatModel model = modelReturning(ChatResponse.builder()
                .aiMessage(AiMessage.from("4"))
                .tokenUsage(new TokenUsage(10, 20))
                .build());

        MeasuredTask task = LangChain4jSupport.measuredTask(model, "<test-model>", PRICES);
        TaskResult result = task.run(Example.of("What is 2+2?", "4"));

        assertThat(result.outputs()).containsEntry("output", "4");
        CallMetrics metrics = result.metrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.tokensIn()).isEqualTo(10);
        assertThat(metrics.tokensOut()).isEqualTo(20);
        // (10 * 0.5 + 20 * 1.5) / 1_000_000 = 35 / 1_000_000
        assertThat(metrics.costUsd()).isEqualTo(35d / 1_000_000d);
        assertThat(metrics.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    void measuredTask_withUsageButNoPriceTable_lightsTokensAndLatencyNotCost() {
        ChatModel model = modelReturning(ChatResponse.builder()
                .aiMessage(AiMessage.from("hi"))
                .tokenUsage(new TokenUsage(7, 3))
                .build());

        MeasuredTask task = LangChain4jSupport.measuredTask(model, "<test-model>", null);
        CallMetrics metrics = task.run(Example.of("q", "a")).metrics();

        assertThat(metrics.tokensIn()).isEqualTo(7);
        assertThat(metrics.tokensOut()).isEqualTo(3);
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isNotNull();
    }

    @Test
    void measuredTask_withUnknownModel_leavesCostNullButKeepsTokens() {
        ChatModel model = modelReturning(ChatResponse.builder()
                .aiMessage(AiMessage.from("hi"))
                .tokenUsage(new TokenUsage(7, 3))
                .build());

        MeasuredTask task = LangChain4jSupport.measuredTask(model, "model-not-in-table", PRICES);
        CallMetrics metrics = task.run(Example.of("q", "a")).metrics();

        assertThat(metrics.tokensIn()).isEqualTo(7);
        assertThat(metrics.costUsd()).isNull();
    }

    @Test
    void measuredTask_withoutUsage_doesNotThrowAndLeavesTokensNull() {
        ChatModel model = modelReturning(
                ChatResponse.builder().aiMessage(AiMessage.from("hi")).build());

        MeasuredTask task = LangChain4jSupport.measuredTask(model, "<test-model>", PRICES);
        CallMetrics metrics = task.run(Example.of("q", "a")).metrics();

        assertThat(metrics.tokensIn()).isNull();
        assertThat(metrics.tokensOut()).isNull();
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isNotNull();
    }

    @Test
    void measuredTask_withAllZeroUsage_treatedAsNotMeasured() {
        ChatModel model = modelReturning(ChatResponse.builder()
                .aiMessage(AiMessage.from("hi"))
                .tokenUsage(new TokenUsage(0, 0))
                .build());

        MeasuredTask task = LangChain4jSupport.measuredTask(model, "<test-model>", PRICES);
        CallMetrics metrics = task.run(Example.of("q", "a")).metrics();

        // An all-zero TokenUsage is a "not measured" sentinel, not a real zero — coalesce to null
        // (matching the Spring AI and Embabel adapters) so the card stays dark, not a false 0.
        assertThat(metrics.tokensIn()).isNull();
        assertThat(metrics.tokensOut()).isNull();
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isNotNull();
    }

    @Test
    void measuredTask_nullModel_throws() {
        assertThatThrownBy(() -> LangChain4jSupport.measuredTask(null, "<test-model>", PRICES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metricsSurviveThroughExperimentToItemResult() {
        ChatModel model = modelReturning(ChatResponse.builder()
                .aiMessage(AiMessage.from("Paris"))
                .tokenUsage(new TokenUsage(12, 5))
                .build());

        Dataset dataset = Dataset.builder()
                .name("capitals")
                .addExample(Example.of("What is the capital of France?", "Paris"))
                .build();

        ExperimentResult result = Experiment.builder()
                .name("measured")
                .dataset(dataset)
                .measuredTask(LangChain4jSupport.measuredTask(model, "<test-model>", PRICES))
                .evaluators(List.of(
                        ExactMatchEvaluator.builder().name("em").threshold(1.0).build()))
                .build()
                .run();

        CallMetrics metrics = result.itemResults().get(0).metrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.tokensIn()).isEqualTo(12);
        assertThat(metrics.tokensOut()).isEqualTo(5);
        assertThat(metrics.costUsd()).isEqualTo((12 * 0.5 + 5 * 1.5) / 1_000_000d);
        assertThat(metrics.latencyMs()).isNotNull();
    }
}
