package dev.dokimos.springai.alibaba;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import dev.dokimos.core.AsyncTask;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.Example;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.TaskResult;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.springai.SpringAiSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

/**
 * Utilities for evaluating Spring AI Alibaba (graph/agent) runs with the Dokimos
 * agent evaluators.
 *
 * <p>Spring AI Alibaba's graph runtime carries its whole conversation as standard
 * Spring AI message types ({@link AssistantMessage}, {@link ToolResponseMessage},
 * and {@code UserMessage}) under the {@link OverAllState} key {@value #MESSAGES_KEY}.
 * Because those are the exact types Dokimos already converts in
 * {@link dev.dokimos.springai.SpringAiSupport}, this class is a thin adapter: it
 * unwraps the {@code List<Message>} from the graph state and folds the full
 * multi-turn conversation into a single {@link AgentTrace}, delegating tool-call
 * and tool-definition conversion to {@code SpringAiSupport}.
 *
 * <h2>Per-turn windowing</h2>
 *
 * <p>Tool-call results are correlated <em>per turn</em>: each {@link AssistantMessage}
 * that issues tool calls is matched only against the {@link ToolResponseMessage}s
 * that follow it, up to the next {@link AssistantMessage}. This avoids silently
 * binding a tool call to the wrong result if a sub-agent or loop reuses a
 * tool-call id across turns.
 *
 * <h2>Judges and tasks</h2>
 *
 * <p>This class deliberately does <em>not</em> provide {@code asJudge} or a plain
 * {@code asyncTask}: Spring AI Alibaba agents run on a standard Spring AI
 * {@code ChatModel}/{@code ChatClient}, so use
 * {@link dev.dokimos.springai.SpringAiSupport#asJudge(org.springframework.ai.chat.model.ChatModel)}
 * and {@link dev.dokimos.springai.SpringAiSupport#asyncTask(org.springframework.ai.chat.client.ChatClient)}
 * directly.
 *
 * <h2>Measured tasks</h2>
 *
 * <p>For metrics capture there is one important exception. A graph run does <em>not</em> expose
 * Spring AI {@link org.springframework.ai.chat.metadata.Usage}: a {@link ReactAgent} run returns a
 * bare {@link AssistantMessage} (or the {@link OverAllState} message list), and a Spring AI
 * {@code AssistantMessage} carries no typed token usage — usage lives only on a
 * {@code ChatResponse}, which the graph fold never surfaces. So unlike
 * {@link dev.dokimos.springai.SpringAiSupport#measuredAsyncTask(org.springframework.ai.chat.client.ChatClient, String, dev.dokimos.core.PriceTable)}
 * (which reads usage off {@code ChatResponse}), the agent path here cannot read usage from its own
 * result. {@link #measuredAsyncTask(Function, String, PriceTable)} therefore follows the decoupled
 * carrier pattern: it auto-times wall-clock latency around the run and lets you supply the token
 * counts you obtain from your own Alibaba setup (a usage callback on the underlying {@code ChatModel},
 * the response metadata, etc.) via {@link AlibabaAgentResponse}, computing cost through an optional
 * {@link PriceTable}. When you drive a plain {@code ChatClient} instead of a graph, prefer
 * {@code SpringAiSupport.measuredAsyncTask(...)} so usage is captured automatically.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * ReactAgent agent = ReactAgent.builder()
 *         .name("assistant")
 *         .chatClient(chatClient)
 *         .tools(toolCallbacks)
 *         .build();
 *
 * AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(
 *         agent, Map.of("messages", List.of(new UserMessage("...")), null));
 *
 * EvalTestCase testCase = trace.toTestCase(
 *         "user question",
 *         SpringAiAlibabaSupport.toToolDefinitions(toolCallbacks));
 *
 * EvalResult result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
 * }</pre>
 */
public final class SpringAiAlibabaSupport {

    /** The Spring AI Alibaba graph-state key under which the message list is stored. */
    public static final String MESSAGES_KEY = "messages";

    private SpringAiAlibabaSupport() {}

    /**
     * Extracts the raw Spring AI {@link Message} list from a graph state.
     *
     * <p>Null-tolerant: a {@code null} state, an absent {@value #MESSAGES_KEY} key,
     * or a value that is not a {@code List} yields an empty list. Elements that are
     * not {@link Message}s (including unknown subtypes that do not implement
     * {@code Message}) are skipped. Never throws.
     *
     * @param state the graph state (may be null)
     * @return the messages in order, or an empty list
     */
    public static List<Message> messages(OverAllState state) {
        if (state == null) {
            return List.of();
        }
        Optional<Object> raw = state.value(MESSAGES_KEY);
        if (raw.isEmpty() || !(raw.get() instanceof List<?> list)) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Message message) {
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * Extracts every tool call across all turns of a graph run, correlating each
     * call to its result with per-turn windowing.
     *
     * <p>For each {@link AssistantMessage} that issues tool calls, the following
     * {@link ToolResponseMessage}s (up to the next {@link AssistantMessage}) form
     * the window used to resolve results, via
     * {@link dev.dokimos.springai.SpringAiSupport#toToolCalls(AssistantMessage, List)}.
     * Calls with no matching response in their window have a {@code null} result.
     *
     * @param state the graph state (may be null)
     * @return the tool calls in execution order, or an empty list
     */
    public static List<ToolCall> toToolCalls(OverAllState state) {
        List<Message> messages = messages(state);
        List<ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof AssistantMessage assistant
                    && assistant.getToolCalls() != null
                    && !assistant.getToolCalls().isEmpty()) {
                List<ToolResponseMessage> window = new ArrayList<>();
                for (int j = i + 1; j < messages.size(); j++) {
                    if (messages.get(j) instanceof AssistantMessage) {
                        break;
                    }
                    if (messages.get(j) instanceof ToolResponseMessage toolResponse) {
                        window.add(toolResponse);
                    }
                }
                calls.addAll(SpringAiSupport.toToolCalls(assistant, window));
            }
        }
        return calls;
    }

    /**
     * Folds the full multi-turn conversation of a graph run into a single
     * {@link AgentTrace}.
     *
     * <p>The trace's tool calls come from {@link #toToolCalls(OverAllState)} (per-turn
     * windowing); its final response is the text of the last {@link AssistantMessage}
     * in the conversation, when that text is non-blank.
     *
     * @param state the graph state (may be null)
     * @return an agent trace, never null
     */
    public static AgentTrace toAgentTrace(OverAllState state) {
        AgentTrace.Builder builder = AgentTrace.builder().toolCalls(toToolCalls(state));
        String last = lastAssistantText(messages(state));
        if (last != null && !last.isBlank()) {
            builder.finalResponse(last);
        }
        return builder.build();
    }

    /**
     * Folds the optional graph state returned by
     * {@link CompiledGraph#invoke(Map)} into a single {@link AgentTrace}.
     *
     * <p>An empty optional yields an empty trace (no tool calls, no final response).
     *
     * @param state the optional graph state (may be null)
     * @return an agent trace, never null
     */
    public static AgentTrace toAgentTrace(Optional<OverAllState> state) {
        return toAgentTrace(state == null ? null : state.orElse(null));
    }

    /**
     * Runs a {@link ReactAgent}'s compiled graph and folds the resulting state into
     * a single {@link AgentTrace}.
     *
     * <p>This is the full-fidelity one-liner: it invokes the compiled graph (which
     * preserves every intermediate tool call) rather than a lossy single-shot call.
     * {@link CompiledGraph#invoke(Map, RunnableConfig)} returns
     * {@code Optional<OverAllState>}, which is folded by
     * {@link #toAgentTrace(Optional)}.
     *
     * @param agent  the agent whose compiled graph is run, never null
     * @param inputs the graph inputs (for example the initial {@value #MESSAGES_KEY} list), never null
     * @param config the run configuration, or {@code null} to invoke without one
     * @return an agent trace, never null
     * @throws GraphStateException if the agent's graph cannot be compiled
     */
    public static AgentTrace toAgentTrace(ReactAgent agent, Map<String, Object> inputs, RunnableConfig config)
            throws GraphStateException {
        // getCompiledGraph() returns null until the graph is built; getAndCompileGraph() compiles it.
        CompiledGraph graph = agent.getAndCompileGraph();
        Optional<OverAllState> state = config == null ? graph.invoke(inputs) : graph.invoke(inputs, config);
        return toAgentTrace(state);
    }

    /**
     * Converts the {@link ToolCallback}s an agent was built with into Dokimos
     * {@link ToolDefinition}s, so tool calls can be evaluated against the tools the
     * agent had available.
     *
     * <p>Delegates to
     * {@link dev.dokimos.springai.SpringAiSupport#toToolDefinitions(List)} after
     * pulling each callback's {@code getToolDefinition()}. A {@code null} or empty
     * list yields an empty list.
     *
     * @param callbacks the tool callbacks supplied to the agent (may be null)
     * @return the Dokimos tool definitions, or an empty list
     */
    public static List<ToolDefinition> toToolDefinitions(List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return List.of();
        }
        return SpringAiSupport.toToolDefinitions(
                callbacks.stream().map(ToolCallback::getToolDefinition).toList());
    }

    /**
     * A Spring AI Alibaba agent run's output text paired with optional token usage, consumed by
     * {@link #measuredAsyncTask(Function, String, PriceTable)}.
     *
     * <p>A graph/agent run does not surface Spring AI {@link org.springframework.ai.chat.metadata.Usage}
     * on its result (see the class-level "Measured tasks" note), so this carrier lets you supply the
     * counts you obtain from your own Alibaba setup (a usage callback on the underlying
     * {@code ChatModel}, the {@code AssistantMessage} metadata, etc.). Leave
     * {@link #tokensIn()}/{@link #tokensOut()} null when you have no counts: latency is still captured
     * automatically and the Cost/Tokens cards simply stay dark. Counts you do supply are taken at face
     * value — an explicit {@code 0} is recorded as zero, not coalesced to null the way the
     * framework-reading adapters (LangChain4j, Spring AI, Embabel) treat an all-zero usage sentinel,
     * since here the caller owns the values.
     *
     * @param text      the agent's output text, or null (stored as an empty string)
     * @param tokensIn  prompt tokens, or null if not available
     * @param tokensOut completion tokens, or null if not available
     */
    public record AlibabaAgentResponse(String text, Integer tokensIn, Integer tokensOut) {

        /**
         * Creates a response carrying output text only, with no token counts (latency-only metrics).
         *
         * @param text the agent's output text, or null
         * @return a response with null token counts
         */
        public static AlibabaAgentResponse of(String text) {
            return new AlibabaAgentResponse(text, null, null);
        }
    }

    /**
     * Creates a measured {@link AsyncTask} that runs a Spring AI Alibaba agent and captures latency
     * automatically and, when a {@link PriceTable} and model id are supplied, cost, lighting up the
     * run's metrics cards.
     *
     * <p>Decoupled-carrier counterpart to
     * {@link dev.dokimos.springai.SpringAiSupport#measuredAsyncTask(org.springframework.ai.chat.client.ChatClient, String, PriceTable)}:
     * a graph/agent run does not expose Spring AI {@link org.springframework.ai.chat.metadata.Usage}
     * (see the class-level "Measured tasks" note), so the supplied {@code agentCall} returns an
     * {@link AlibabaAgentResponse} carrying the output text plus any token counts you extracted from
     * your own setup. Wall-clock latency is measured around the call; cost is computed via
     * {@code prices.costUsd(model, tokensIn, tokensOut)} when both {@code prices} and {@code model}
     * are non-null. Missing token counts leave those fields null (only the Latency card lights); a
     * null {@code prices} or {@code model} leaves cost null. The output text is written under the
     * {@link dev.dokimos.springai.SpringAiSupport#OUTPUT_KEY default output key}.
     *
     * <p>The blocking call runs on the common {@link java.util.concurrent.ForkJoinPool}; for isolated,
     * true concurrency use {@link #measuredAsyncTask(Function, String, PriceTable, Executor)} with a
     * pool you size to the experiment's {@code parallelism}. Never throws on missing metrics.
     *
     * <p>Example:
     * <pre>{@code
     * ReactAgent agent = ReactAgent.builder().name("assistant").chatClient(chatClient).build();
     * AsyncTask task = SpringAiAlibabaSupport.measuredAsyncTask(
     *         example -> {
     *             AssistantMessage out = agent.call(example.input());
     *             // pull token counts from your own usage callback / response metadata:
     *             return new AlibabaAgentResponse(out.getText(), promptTokens, completionTokens);
     *         },
     *         "qwen-max",
     *         prices);
     * }</pre>
     *
     * @param agentCall a function that runs the agent for an {@link Example} and returns its response,
     *                  never null
     * @param model     the model id used as the {@link PriceTable} lookup key, or null to skip pricing
     * @param prices    the price lookup, or null to capture tokens and latency only
     * @return an AsyncTask suitable for {@code Experiment.builder().asyncTask(...)}
     * @throws IllegalArgumentException if {@code agentCall} is null
     */
    public static AsyncTask measuredAsyncTask(
            Function<Example, AlibabaAgentResponse> agentCall, String model, PriceTable prices) {
        return measuredTask(agentCall, model, prices, null);
    }

    /**
     * Creates a measured {@link AsyncTask} that runs a Spring AI Alibaba agent on the supplied
     * {@link Executor} so you control and isolate concurrency, with a non-null executor required.
     *
     * @param agentCall a function that runs the agent for an {@link Example} and returns its response,
     *                  never null
     * @param model     the model id used as the {@link PriceTable} lookup key, or null to skip pricing
     * @param prices    the price lookup, or null to capture tokens and latency only
     * @param executor  the executor each blocking call runs on, never null
     * @return an AsyncTask suitable for {@code Experiment.builder().asyncTask(...)}
     * @throws IllegalArgumentException if {@code agentCall} or {@code executor} is null
     */
    public static AsyncTask measuredAsyncTask(
            Function<Example, AlibabaAgentResponse> agentCall, String model, PriceTable prices, Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        return measuredTask(agentCall, model, prices, executor);
    }

    // Shared implementation, tolerating a null executor (common pool). Distinct name avoids clashing
    // with the public four-arg overload.
    private static AsyncTask measuredTask(
            Function<Example, AlibabaAgentResponse> agentCall, String model, PriceTable prices, Executor executor) {
        if (agentCall == null) {
            throw new IllegalArgumentException("agentCall cannot be null");
        }
        return example -> {
            Supplier<TaskResult> call = () -> {
                long start = System.nanoTime();
                AlibabaAgentResponse response = agentCall.apply(example);
                long latencyMs = (System.nanoTime() - start) / 1_000_000L;
                String text = (response == null || response.text() == null) ? "" : response.text();
                Integer tokensIn = response == null ? null : response.tokensIn();
                Integer tokensOut = response == null ? null : response.tokensOut();
                Double costUsd = (prices != null && model != null) ? prices.costUsd(model, tokensIn, tokensOut) : null;
                return new TaskResult(
                        Map.of(SpringAiSupport.OUTPUT_KEY, text),
                        new CallMetrics(tokensIn, tokensOut, costUsd, latencyMs));
            };
            return executor == null
                    ? CompletableFuture.supplyAsync(call)
                    : CompletableFuture.supplyAsync(call, executor);
        };
    }

    private static String lastAssistantText(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage assistant) {
                return assistant.getText();
            }
        }
        return null;
    }
}
