package dev.dokimos.embabel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.embabel.agent.core.ProcessOptions;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.embabel.EmbabelSupport;
import dev.dokimos.embabel.EmbabelTraceCollector;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration coverage for the Embabel adapter.
 *
 * <p><strong>Compile-only / wiring smoke test by design.</strong> A fully live test needs an
 * Embabel {@code AgentPlatform} wired through {@code embabel-agent-starter-openai}, which at 0.4.0
 * pulls experimental Spring dependencies from the Spring Milestones repo. To keep the module's build
 * self-contained and Maven-Central-publishable, this IT does not stand up a live platform; it
 * exercises the public adapter surface against the real Embabel {@code ProcessOptions} type and the
 * Dokimos evaluators, so it fails to compile if either API drifts.
 *
 * <p>To run a genuinely live agent here, add {@code embabel-agent-starter-openai} at test scope plus
 * the Spring Milestones repository, build a platform + one tool, attach the collector via
 * {@link EmbabelSupport#attach(ProcessOptions, EmbabelTraceCollector)}, run an
 * {@code AgentInvocation}, then assert {@code collector.trace()} captured the tool call. Tagged
 * {@code integration} so {@code mvn test} skips it; it runs only under
 * {@code mvn verify -Dgroups=integration}.
 */
@Tag("integration")
class EmbabelToolTraceIT {

    @Test
    void attachReturnsOptionsCarryingTheCollectorListener() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        ProcessOptions options = EmbabelSupport.attach(new ProcessOptions(), collector);

        assertThat(options).isNotNull();
        assertThat(options.getListeners()).contains(collector);
    }

    @Test
    void emptyRunProducesAnEvaluableTestCase() {
        // Mirrors the live flow's tail: with no events, trace() is empty but still round-trips
        // through the agent evaluators without throwing (the failure-path / silent-drop contract).
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        AgentTrace trace = collector.trace();
        List<ToolDefinition> tools = EmbabelSupport.toToolDefinitions(collector);
        EvalTestCase testCase = trace.toTestCase("any input", tools);

        assertThat(trace.finalResponse()).isNull();
        assertThatCode(() -> ToolCallValidityEvaluator.builder().build().evaluate(testCase))
                .doesNotThrowAnyException();
    }
}
