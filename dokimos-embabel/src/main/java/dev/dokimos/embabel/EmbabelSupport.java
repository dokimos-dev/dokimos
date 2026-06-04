package dev.dokimos.embabel;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.ProcessOptions;
import dev.dokimos.core.agents.ToolDefinition;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for evaluating Embabel agent runs with Dokimos.
 *
 * <p>Embabel reports tool calls only through event callbacks during a run, so the integration is
 * built around a stateful {@link EmbabelTraceCollector} (an {@code AgenticEventListener}) plus the
 * installers on this class. The flow is: attach a collector to the run's options, execute the agent,
 * then read the trace.
 *
 * <pre>{@code
 * EmbabelTraceCollector collector = new EmbabelTraceCollector();
 * ProcessOptions options = EmbabelSupport.attach(new ProcessOptions(), collector);
 *
 * AgentInvocation<MyResult> inv = AgentInvocation.builder(platform)
 *         .options(options)
 *         .build(MyResult.class);
 * inv.invoke(userInput);
 *
 * AgentTrace trace = collector.trace();
 * EvalTestCase tc = trace.toTestCase(userInput, EmbabelSupport.toToolDefinitions(collector));
 * }</pre>
 */
public final class EmbabelSupport {

    private EmbabelSupport() {}

    /**
     * Registers the given collector as a listener on a copy of the supplied {@link ProcessOptions}.
     *
     * <p>{@code ProcessOptions} is immutable: {@link ProcessOptions#withListener} returns a new
     * instance. Use the returned options when building the {@link AgentInvocation} so the collector
     * receives the run's events.
     *
     * @param options   the base process options to copy, never null
     * @param collector the collector to register, never null
     * @return a new {@link ProcessOptions} with the collector attached
     * @throws IllegalArgumentException if {@code options} or {@code collector} is null
     */
    public static ProcessOptions attach(ProcessOptions options, EmbabelTraceCollector collector) {
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null");
        }
        if (collector == null) {
            throw new IllegalArgumentException("collector cannot be null");
        }
        return options.withListener(collector);
    }

    /**
     * Creates a fresh collector and registers it on a default {@link ProcessOptions}.
     *
     * <p>Read the resulting options off the returned collector is not possible directly; prefer
     * {@link #attach(ProcessOptions, EmbabelTraceCollector)} when you need to keep the options. This
     * convenience overload registers the collector on the {@link AgentInvocation.Builder} instead and
     * returns the collector for {@link EmbabelTraceCollector#trace()} after the run.
     *
     * @param invocationBuilder the invocation builder to configure, never null
     * @return the registered collector
     * @throws IllegalArgumentException if {@code invocationBuilder} is null
     */
    public static EmbabelTraceCollector attach(AgentInvocation.Builder invocationBuilder) {
        if (invocationBuilder == null) {
            throw new IllegalArgumentException("invocationBuilder cannot be null");
        }
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        invocationBuilder.options(new ProcessOptions().withListener(collector));
        return collector;
    }

    /**
     * Synthesizes {@link ToolDefinition}s from the tool names observed during a run.
     *
     * <p>Embabel's event stream does not carry the tools' JSON schemas, so each synthesized
     * definition has an empty input schema and an empty description. This is sufficient for
     * evaluators that key off tool names (for example {@code ToolCallValidityEvaluator}) but
     * <strong>weakens {@code ToolDescriptionReliabilityEvaluator}</strong>, which has no description
     * to assess. If you have the original tool contracts, build {@link ToolDefinition}s from those
     * instead. (Decision O2.)
     *
     * @param collector the collector that observed the run, never null
     * @return one tool definition per observed tool name, with an empty schema
     * @throws IllegalArgumentException if {@code collector} is null
     */
    public static List<ToolDefinition> toToolDefinitions(EmbabelTraceCollector collector) {
        if (collector == null) {
            throw new IllegalArgumentException("collector cannot be null");
        }
        List<ToolDefinition> definitions = new ArrayList<>();
        for (String name : collector.observedToolNames()) {
            definitions.add(ToolDefinition.builder().name(name).description("").build());
        }
        return List.copyOf(definitions);
    }
}
