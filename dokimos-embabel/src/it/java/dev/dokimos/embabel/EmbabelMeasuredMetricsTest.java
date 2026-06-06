package dev.dokimos.embabel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.embabel.agent.api.event.AgentProcessCompletedEvent;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.LlmInvocation;
import com.embabel.agent.core.Usage;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.PriceTable;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link EmbabelTraceCollector} captures token usage, latency, and cost off the completed
 * {@link AgentProcess}. Unlike Spring AI, Embabel exposes usage and a computed cost as an aggregate on
 * the process ({@code totalUsage()} / {@code totalCost()} / per-invocation running times), so the
 * collector snapshots them at completion and exposes them as {@link CallMetrics}.
 *
 * <p>Embabel's event and core types are final Kotlin classes, mocked here via the inline mock-maker
 * (the same approach the rest of this module's tests use).
 */
class EmbabelMeasuredMetricsTest {

    private static final PriceTable PRICES = (model, in, out) -> {
        if (!"<test-model>".equals(model) || in == null || out == null) {
            return null;
        }
        return (in * 0.5 + out * 1.5) / 1_000_000d;
    };

    private static Usage usage(Integer promptTokens, Integer completionTokens) {
        Usage usage = mock(Usage.class);
        lenient().when(usage.getPromptTokens()).thenReturn(promptTokens);
        lenient().when(usage.getCompletionTokens()).thenReturn(completionTokens);
        return usage;
    }

    private static LlmInvocation invocation(long runningTimeMillis) {
        LlmInvocation invocation = mock(LlmInvocation.class);
        lenient().when(invocation.getRunningTime()).thenReturn(Duration.ofMillis(runningTimeMillis));
        return invocation;
    }

    private static AgentProcessCompletedEvent completed(
            Object result, Usage totalUsage, double totalCost, List<LlmInvocation> invocations) {
        AgentProcess process = mock(AgentProcess.class);
        lenient().when(process.totalUsage()).thenReturn(totalUsage);
        lenient().when(process.totalCost()).thenReturn(totalCost);
        lenient().when(process.getLlmInvocations()).thenReturn(invocations);

        AgentProcessCompletedEvent event = mock(AgentProcessCompletedEvent.class);
        lenient().when(event.getResult()).thenReturn(result);
        lenient().when(event.getAgentProcess()).thenReturn(process);
        return event;
    }

    @Test
    void capturesTokensCostAndLatencyFromCompletedProcess() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(completed("answer", usage(10, 20), 0.0003, List.of(invocation(120), invocation(80))));

        CallMetrics metrics = collector.callMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.tokensIn()).isEqualTo(10);
        assertThat(metrics.tokensOut()).isEqualTo(20);
        // Embabel's own computed cost is used verbatim.
        assertThat(metrics.costUsd()).isEqualTo(0.0003);
        // Latency is the sum of per-invocation running times.
        assertThat(metrics.latencyMs()).isEqualTo(200L);
    }

    @Test
    void embabelZeroCostFallsBackToPriceTableWhenModelKnown() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(completed("answer", usage(10, 20), 0.0, List.of(invocation(50))));

        CallMetrics metrics = collector.callMetrics("<test-model>", PRICES);
        assertThat(metrics.tokensIn()).isEqualTo(10);
        // Embabel reported $0 (unknown to its pricing), so the PriceTable fallback computes the cost.
        assertThat(metrics.costUsd()).isEqualTo((10 * 0.5 + 20 * 1.5) / 1_000_000d);
        assertThat(metrics.latencyMs()).isEqualTo(50L);
    }

    @Test
    void embabelNonZeroCostWinsOverPriceTableFallback() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(completed("answer", usage(10, 20), 0.0009, List.of(invocation(50))));

        // The fallback PriceTable would yield a different number, but Embabel's own cost wins.
        CallMetrics metrics = collector.callMetrics("<test-model>", PRICES);
        assertThat(metrics.costUsd()).isEqualTo(0.0009);
    }

    @Test
    void allZeroUsageIsTreatedAsNotMeasured() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(completed("answer", usage(0, 0), 0.0, List.of(invocation(40))));

        CallMetrics metrics = collector.callMetrics();
        assertThat(metrics).isNotNull();
        // The 0/0 sentinel is coalesced to null so the Tokens card stays dark rather than showing 0.
        assertThat(metrics.tokensIn()).isNull();
        assertThat(metrics.tokensOut()).isNull();
        assertThat(metrics.costUsd()).isNull();
        // Latency was genuinely measured.
        assertThat(metrics.latencyMs()).isEqualTo(40L);
    }

    @Test
    void zeroEmbabelCostWithoutPriceTableLeavesCostNull() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(completed("answer", usage(10, 20), 0.0, List.of(invocation(30))));

        CallMetrics metrics = collector.callMetrics();
        assertThat(metrics.tokensIn()).isEqualTo(10);
        assertThat(metrics.costUsd()).isNull();
        assertThat(metrics.latencyMs()).isEqualTo(30L);
    }

    @Test
    void noCompletionEventYieldsNullCallMetrics() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        assertThat(collector.callMetrics()).isNull();
        assertThat(collector.callMetrics("<test-model>", PRICES)).isNull();
    }

    @Test
    void resetClearsCapturedMetrics() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(completed("answer", usage(10, 20), 0.0003, List.of(invocation(120))));
        assertThat(collector.callMetrics()).isNotNull();

        collector.reset();
        assertThat(collector.callMetrics()).isNull();
    }
}
