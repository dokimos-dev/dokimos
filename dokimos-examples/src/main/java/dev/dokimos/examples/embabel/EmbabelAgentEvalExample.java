package dev.dokimos.examples.embabel;

import com.embabel.agent.core.ProcessOptions;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.embabel.EmbabelSupport;
import dev.dokimos.embabel.EmbabelTraceCollector;
import java.util.List;

/**
 * Evaluates an Embabel agent run with Dokimos.
 *
 * <p>Embabel reports tool activity only through event callbacks during a run, so the integration is
 * built around a stateful {@link EmbabelTraceCollector} (an Embabel {@code AgenticEventListener}).
 * The flow is always the same:
 *
 * <ol>
 *   <li>Create a collector and attach it to the run's {@link ProcessOptions} via
 *       {@link EmbabelSupport#attach(ProcessOptions, EmbabelTraceCollector)}.
 *   <li>Run the agent with those options (an {@code AgentInvocation} against a live
 *       {@code AgentPlatform}).
 *   <li>Read the captured run off the collector with {@link EmbabelTraceCollector#trace()} and turn
 *       it into a Dokimos {@link EvalTestCase}.
 *   <li>Score it with the agent evaluators.
 * </ol>
 *
 * <p><strong>Why this example is wiring-only.</strong> Standing up a live Embabel
 * {@code AgentPlatform} needs {@code embabel-agent-starter-openai}, which at 0.4.0 pulls experimental
 * Spring dependencies from the Spring Milestones repo. To keep this module self-contained, the
 * example exercises the full adapter surface against the real Embabel {@link ProcessOptions} type and
 * the Dokimos evaluators without booting a platform. To make it genuinely live, add
 * {@code embabel-agent-starter-openai} (and the Spring Milestones repository), build a platform plus
 * one {@code @Tool}, run an {@code AgentInvocation} with {@code options}, and the same
 * {@code collector.trace()} call below will carry the real tool calls.
 *
 * <p>A collector observes a single run; reuse it across runs only after {@link
 * EmbabelTraceCollector#reset()}.
 */
public class EmbabelAgentEvalExample {

    public static void main(String[] args) {
        String userInput = "Find me a peaty Islay whisky around 12 years old";

        // 1. Create a collector and attach it to the run's process options.
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        ProcessOptions options = EmbabelSupport.attach(new ProcessOptions(), collector);

        // 2. Run the agent with these options. In a live app:
        //
        //    AgentInvocation<MyResult> inv = AgentInvocation.builder(platform)
        //            .options(options)
        //            .build(MyResult.class);
        //    inv.invoke(userInput);
        //
        // The collector receives a ToolCallResponseEvent per tool call and an
        // AgentProcessCompletedEvent for the final result. With no live run here, the
        // trace is empty but still round-trips through the evaluators.
        System.out.println("Attached collector to ProcessOptions: "
                + options.getListeners().contains(collector));

        // 3. Read the captured run and build a Dokimos test case.
        AgentTrace trace = collector.trace();
        // Embabel's event stream carries tool names but not their JSON schemas, so the synthesized
        // definitions have empty schemas (this weakens ToolDescriptionReliabilityEvaluator). If you
        // have the original tool contracts, build ToolDefinitions from those instead.
        List<ToolDefinition> tools = EmbabelSupport.toToolDefinitions(collector);
        EvalTestCase testCase = trace.toTestCase(userInput, tools, List.of(userInput));

        // 4. Score the run with a deterministic agent evaluator (no LLM needed).
        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);

        System.out.println("User input:     " + userInput);
        System.out.println("Tool calls:     " + trace.toolNames());
        System.out.printf("Tool Call Validity: %.2f (%s)%n", validity.score(), validity.reason());
        System.out.println("Final response: " + trace.finalResponse());
    }
}
