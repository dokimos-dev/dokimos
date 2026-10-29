package dev.dokimos.adk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.conversation.ConversationTrajectory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * An ADK {@link BasePlugin} that passively observes an agent run and accumulates its events,
 * tool calls, tool definitions, multi-turn conversation trajectory, retrieved RAG contexts,
 * and {@link CallMetrics} into Dokimos evaluation types.
 *
 * <p>Register the collector on a {@link com.google.adk.runner.Runner.Builder} (via
 * {@code Runner.builder().plugins(collector)} or {@link AdkSupport#attach}), run your agent as
 * usual, and then read {@link #trace()}, {@link #toolDefinitions()}, {@link #trajectory()}, or
 * {@link #callMetrics(String, PriceTable)}:
 *
 * <pre>{@code
 * Runner.Builder builder = Runner.builder().agent(agent).appName("my-app");
 * AdkTraceCollector collector = AdkSupport.attach(builder);
 * Runner runner = builder.build();
 *
 * // Run the agent as usual
 * runner.runAsync("user-1", "session-1", Content.fromParts(Part.fromText(userInput)))
 *       .blockingSubscribe();
 *
 * AgentTrace trace = collector.trace();
 * List<ToolDefinition> tools = collector.toolDefinitions();
 * EvalTestCase testCase = trace.toTestCase(userInput, tools);
 * }</pre>
 *
 * <p>Unlike trace collectors in frameworks that only expose tool names on events, ADK's
 * {@link #beforeToolCallback} and {@link #afterToolCallback} receive the live {@link BaseTool}
 * instance, so {@link #toolDefinitions()} preserves each tool's full description and parameter
 * JSON schema.
 *
 * <p>This collector is stateful and intended for a single evaluation run or multi-turn session.
 * Call {@link #reset()} before reusing the same instance across independent runs.
 */
public final class AdkTraceCollector extends BasePlugin {

    /** Default ADK plugin name used when none is specified. */
    public static final String DEFAULT_PLUGIN_NAME = "dokimos-trace-collector";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final List<LlmResponse> llmResponses = new CopyOnWriteArrayList<>();
    private final List<ToolCall> callbackToolCalls = new CopyOnWriteArrayList<>();
    private final Map<String, BaseTool> observedTools = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<String> observedToolNames = Collections.synchronizedSet(new LinkedHashSet<>());

    private volatile Long runStartNanos;
    private volatile Long totalLatencyMs;
    private volatile boolean hasObservedActivity;
    private volatile String observedModelId;

    /**
     * Creates a new trace collector plugin with the {@link #DEFAULT_PLUGIN_NAME}.
     */
    public AdkTraceCollector() {
        this(DEFAULT_PLUGIN_NAME);
    }

    /**
     * Creates a new trace collector plugin with a custom plugin name.
     *
     * @param name the plugin name (must not be null)
     */
    public AdkTraceCollector(String name) {
        super(Objects.requireNonNull(name, "name must not be null"));
    }

    @Override
    public Maybe<Content> onUserMessageCallback(InvocationContext invocationContext, Content userMessage) {
        if (userMessage != null) {
            hasObservedActivity = true;
            String invocationId = invocationContext != null && invocationContext.invocationId() != null
                    ? invocationContext.invocationId()
                    : "";
            Event userEvent = Event.builder()
                    .id(Event.generateEventId())
                    .invocationId(invocationId)
                    .author("user")
                    .content(userMessage)
                    .build();
            events.add(userEvent);
        }
        return Maybe.empty();
    }

    @Override
    public Maybe<Content> beforeRunCallback(InvocationContext invocationContext) {
        hasObservedActivity = true;
        runStartNanos = System.nanoTime();
        if (invocationContext != null) {
            BaseAgent agent = invocationContext.agent();
            if (agent != null) {
                for (BaseTool tool : AdkSupport.collectTools(agent)) {
                    if (tool != null && tool.name() != null && !tool.name().isBlank()) {
                        observedTools.putIfAbsent(tool.name(), tool);
                    }
                }
                if (observedModelId == null) {
                    observedModelId = AdkSupport.resolveModelId(agent);
                }
            }
            if (invocationContext.userContent().isPresent()) {
                Content userContent = invocationContext.userContent().get();
                String invocationId = invocationContext.invocationId() != null ? invocationContext.invocationId() : "";
                if (!hasMatchingTrailingUserEvent(invocationId)) {
                    events.add(Event.builder()
                            .id(Event.generateEventId())
                            .invocationId(invocationId)
                            .author("user")
                            .content(userContent)
                            .build());
                }
            }
        }
        return Maybe.empty();
    }

    @Override
    public Maybe<Event> onEventCallback(InvocationContext invocationContext, Event event) {
        if (event != null) {
            hasObservedActivity = true;
            if (!isDuplicateUserEvent(event)) {
                events.add(event);
            }
            if (observedModelId == null
                    && event.modelVersion().isPresent()
                    && !event.modelVersion().get().isBlank()) {
                observedModelId = event.modelVersion().get();
            }
            if (event.functionCalls() != null) {
                event.functionCalls().forEach(call -> {
                    if (call != null && call.name().isPresent()) {
                        String name = call.name().get();
                        if (!name.isBlank()) {
                            observedToolNames.add(name);
                        }
                    }
                });
            }
        }
        return Maybe.empty();
    }

    @Override
    public Maybe<LlmResponse> afterModelCallback(CallbackContext callbackContext, LlmResponse llmResponse) {
        if (llmResponse != null) {
            hasObservedActivity = true;
            llmResponses.add(llmResponse);
            if (observedModelId == null
                    && llmResponse.modelVersion().isPresent()
                    && !llmResponse.modelVersion().get().isBlank()) {
                observedModelId = llmResponse.modelVersion().get();
            }
        }
        return Maybe.empty();
    }

    @Override
    public Maybe<Map<String, Object>> beforeToolCallback(
            BaseTool tool, Map<String, Object> toolArgs, ToolContext toolContext) {
        recordObservedTool(tool);
        return Maybe.empty();
    }

    @Override
    public Maybe<Map<String, Object>> afterToolCallback(
            BaseTool tool, Map<String, Object> toolArgs, ToolContext toolContext, Map<String, Object> result) {
        hasObservedActivity = true;
        recordObservedTool(tool);
        if (tool != null && tool.name() != null && !tool.name().isBlank()) {
            ToolCall.Builder builder =
                    ToolCall.builder().name(tool.name()).arguments(toolArgs != null ? toolArgs : Map.of());
            String serializedResult = serializeResultMap(result);
            if (serializedResult != null) {
                builder.result(serializedResult);
            }
            builder.metadata("error", AdkSupport.isErrorMap(result));
            if (toolContext != null
                    && toolContext.functionCallId().isPresent()
                    && !toolContext.functionCallId().get().isBlank()) {
                builder.metadata("id", toolContext.functionCallId().get());
            }
            callbackToolCalls.add(builder.build());
        }
        return Maybe.empty();
    }

    @Override
    public Maybe<Map<String, Object>> onToolErrorCallback(
            BaseTool tool, Map<String, Object> toolArgs, ToolContext toolContext, Throwable error) {
        hasObservedActivity = true;
        recordObservedTool(tool);
        if (tool != null && tool.name() != null && !tool.name().isBlank()) {
            String errorMsg = error != null
                    ? (error.getMessage() != null
                            ? error.getMessage()
                            : error.getClass().getName())
                    : "unknown error";
            ToolCall.Builder builder = ToolCall.builder()
                    .name(tool.name())
                    .arguments(toolArgs != null ? toolArgs : Map.of())
                    .result(serializeResultMap(Map.of("error", errorMsg)))
                    .metadata("error", true);
            if (error != null) {
                builder.metadata("exceptionType", error.getClass().getName());
            }
            if (toolContext != null
                    && toolContext.functionCallId().isPresent()
                    && !toolContext.functionCallId().get().isBlank()) {
                builder.metadata("id", toolContext.functionCallId().get());
            }
            callbackToolCalls.add(builder.build());
        }
        return Maybe.empty();
    }

    @Override
    public Completable afterRunCallback(InvocationContext invocationContext) {
        return Completable.fromAction(() -> {
            hasObservedActivity = true;
            Long start = runStartNanos;
            if (start != null) {
                long elapsedMs = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                Long prev = totalLatencyMs;
                totalLatencyMs = (prev == null ? 0L : prev) + elapsedMs;
                runStartNanos = null;
            }
        });
    }

    /**
     * Returns the ADK {@link Event}s captured so far, in chronological order.
     *
     * @return an immutable copy of the captured events
     */
    public List<Event> events() {
        return List.copyOf(events);
    }

    /**
     * Returns the {@link ToolCall}s captured so far, in execution order.
     * <p>
     * Extracts matched {@code FunctionCall}/{@code FunctionResponse} pairs from captured
     * {@link Event}s when present, backfilling any missing results or error metadata from
     * {@link #afterToolCallback} / {@link #onToolErrorCallback}, and falling back to callback
     * tool calls when no tool-call events were emitted.
     *
     * @return an immutable list of captured tool calls
     */
    public List<ToolCall> toolCalls() {
        return mergeCallbackResults(AdkSupport.toToolCalls(events), callbackToolCalls);
    }

    /**
     * Returns the distinct tool names invoked during the run, in first-seen order.
     *
     * @return an immutable copy of the observed tool names
     */
    public List<String> observedToolNames() {
        synchronized (observedToolNames) {
            return List.copyOf(observedToolNames);
        }
    }

    /**
     * Returns {@link ToolDefinition}s for all tools configured on the agent or observed during the run.
     * <p>
     * For tools discovered on the agent in {@link #beforeRunCallback} or observed via
     * {@link #beforeToolCallback}, {@link #afterToolCallback}, or {@link #onToolErrorCallback},
     * the full {@link BaseTool} declaration (description and JSON parameter schema) is converted
     * via {@link AdkSupport#toToolDefinition(BaseTool)}. For tool names observed only on
     * {@link Event}s without a {@link BaseTool} instance, a minimal {@link ToolDefinition} with
     * an empty object schema is synthesized.
     *
     * @return an immutable list of tool definitions in first-seen order
     */
    public List<ToolDefinition> toolDefinitions() {
        Set<String> allNames = new LinkedHashSet<>();
        synchronized (observedToolNames) {
            allNames.addAll(observedToolNames);
        }
        synchronized (observedTools) {
            allNames.addAll(observedTools.keySet());
        }
        List<ToolDefinition> definitions = new ArrayList<>(allNames.size());
        for (String name : allNames) {
            BaseTool tool = observedTools.get(name);
            if (tool != null) {
                definitions.add(AdkSupport.toToolDefinition(tool));
            } else {
                definitions.add(ToolDefinition.of(name, "", Map.of("type", "object", "properties", Map.of())));
            }
        }
        return List.copyOf(definitions);
    }

    /**
     * Extracts retrieved RAG context strings from the captured events (both
     * {@code GroundingMetadata} chunks and retrieval tool responses).
     *
     * @param retrievalToolNames optional names of retrieval tools to filter {@code FunctionResponse}s by
     * @return an immutable list of extracted context strings
     */
    public List<String> retrievedContexts(String... retrievalToolNames) {
        return AdkSupport.extractTexts(events, retrievalToolNames);
    }

    /**
     * Materializes the captured run into a Dokimos {@link AgentTrace}.
     *
     * @return the extracted AgentTrace
     */
    public AgentTrace trace() {
        AgentTrace fromEvents = AdkSupport.toAgentTrace(events);
        List<ToolCall> mergedCalls = mergeCallbackResults(fromEvents.toolCalls(), callbackToolCalls);
        if (mergedCalls.equals(fromEvents.toolCalls())) {
            return fromEvents;
        }
        AgentTrace.Builder builder =
                AgentTrace.builder().toolCalls(mergedCalls).finalResponse(fromEvents.finalResponse());
        fromEvents.reasoningSteps().forEach(builder::addReasoningStep);
        fromEvents.metadata().forEach(builder::metadata);
        return builder.build();
    }

    /**
     * Materializes the captured events into a multi-turn {@link ConversationTrajectory}.
     *
     * @return the extracted ConversationTrajectory
     */
    public ConversationTrajectory trajectory() {
        return AdkSupport.toConversationTrajectory(events);
    }

    /**
     * Materializes the captured events into a multi-turn {@link ConversationTrajectory}
     * with the given {@code scenario} description.
     *
     * @param scenario the scenario description (may be null)
     * @return the extracted ConversationTrajectory
     */
    public ConversationTrajectory trajectory(String scenario) {
        return AdkSupport.toConversationTrajectory(events, scenario);
    }

    /**
     * Returns the {@link CallMetrics} captured from the completed run, with no price lookup.
     *
     * @return the captured call metrics, or {@code null} if no run activity was observed
     */
    public CallMetrics callMetrics() {
        return callMetrics(null, null);
    }

    /**
     * Returns the {@link CallMetrics} captured from the completed run, computing USD cost
     * via the supplied {@link PriceTable} using the model identifier inferred from the agent
     * or events.
     *
     * @param prices the price table for cost lookup (may be null)
     * @return the captured call metrics, or {@code null} if no run activity was observed
     */
    public CallMetrics callMetrics(PriceTable prices) {
        return callMetrics(null, prices);
    }

    /**
     * Returns the {@link CallMetrics} captured from the completed run, computing USD cost
     * via the optional {@link PriceTable} when token usage was recorded.
     *
     * @param modelId the model identifier for cost lookup (may be null; falls back to the model
     *                observed on the agent or events)
     * @param prices  the price table for cost lookup (may be null)
     * @return the captured call metrics, or {@code null} if no run activity was observed
     */
    public CallMetrics callMetrics(String modelId, PriceTable prices) {
        if (!hasObservedActivity) {
            return null;
        }
        String effectiveModelId = (modelId != null && !modelId.isBlank()) ? modelId : observedModelId;
        long latency = totalLatencyMs != null ? totalLatencyMs : 0L;
        CallMetrics fromEvents = AdkSupport.toCallMetrics(events, latency, effectiveModelId, prices);
        if (fromEvents.tokensIn() != null || fromEvents.tokensOut() != null || llmResponses.isEmpty()) {
            if (fromEvents.tokensIn() == null && fromEvents.tokensOut() == null && totalLatencyMs == null) {
                return null;
            }
            return new CallMetrics(fromEvents.tokensIn(), fromEvents.tokensOut(), fromEvents.costUsd(), totalLatencyMs);
        }

        // Fallback: aggregate usageMetadata from LlmResponses captured via afterModelCallback
        int pSum = 0;
        int cSum = 0;
        for (LlmResponse response : llmResponses) {
            if (response != null
                    && !response.partial().orElse(false)
                    && response.usageMetadata().isPresent()) {
                GenerateContentResponseUsageMetadata u =
                        response.usageMetadata().get();
                pSum += u.promptTokenCount().orElse(0);
                cSum += u.candidatesTokenCount().orElse(0);
            }
        }
        Integer tokensIn = (pSum > 0 || cSum > 0) ? pSum : null;
        Integer tokensOut = (pSum > 0 || cSum > 0) ? cSum : null;
        if (tokensIn == null && tokensOut == null && totalLatencyMs == null) {
            return null;
        }
        Double costUsd = (prices != null && effectiveModelId != null && tokensIn != null && tokensOut != null)
                ? prices.costUsd(effectiveModelId, tokensIn, tokensOut)
                : null;
        return new CallMetrics(tokensIn, tokensOut, costUsd, totalLatencyMs);
    }

    /**
     * Clears all captured events, tool calls, tool definitions, and metrics so this collector
     * can observe a fresh run.
     */
    public void reset() {
        events.clear();
        llmResponses.clear();
        callbackToolCalls.clear();
        observedTools.clear();
        observedToolNames.clear();
        runStartNanos = null;
        totalLatencyMs = null;
        hasObservedActivity = false;
        observedModelId = null;
    }

    private void recordObservedTool(BaseTool tool) {
        if (tool != null && tool.name() != null && !tool.name().isBlank()) {
            observedTools.putIfAbsent(tool.name(), tool);
            observedToolNames.add(tool.name());
        }
    }

    private static List<ToolCall> mergeCallbackResults(List<ToolCall> fromEvents, List<ToolCall> fromCallbacks) {
        if (fromEvents.isEmpty()) {
            return List.copyOf(fromCallbacks);
        }
        if (fromCallbacks.isEmpty()) {
            return fromEvents;
        }
        List<ToolCall> callbacks = new ArrayList<>(fromCallbacks);
        boolean[] usedCallback = new boolean[callbacks.size()];
        List<ToolCall> merged = new ArrayList<>(fromEvents.size());
        for (ToolCall eventCall : fromEvents) {
            if (eventCall.result() != null) {
                merged.add(eventCall);
                continue;
            }
            ToolCall matchedCb = null;
            Object callId = eventCall.metadata().get("id");
            if (callId instanceof String idStr && !idStr.isBlank()) {
                for (int i = 0; i < callbacks.size(); i++) {
                    if (!usedCallback[i]
                            && idStr.equals(callbacks.get(i).metadata().get("id"))) {
                        matchedCb = callbacks.get(i);
                        usedCallback[i] = true;
                        break;
                    }
                }
            }
            if (matchedCb == null) {
                for (int i = 0; i < callbacks.size(); i++) {
                    if (!usedCallback[i]
                            && eventCall.name().equals(callbacks.get(i).name())) {
                        matchedCb = callbacks.get(i);
                        usedCallback[i] = true;
                        break;
                    }
                }
            }
            if (matchedCb != null) {
                Map<String, Object> combinedMeta = new LinkedHashMap<>(eventCall.metadata());
                combinedMeta.putAll(matchedCb.metadata());
                merged.add(ToolCall.builder()
                        .name(eventCall.name())
                        .arguments(eventCall.arguments())
                        .result(matchedCb.result())
                        .metadata(combinedMeta)
                        .build());
            } else {
                merged.add(eventCall);
            }
        }
        return List.copyOf(merged);
    }

    private boolean hasMatchingTrailingUserEvent(String invocationId) {
        if (events.isEmpty()) {
            return false;
        }
        Event last = events.get(events.size() - 1);
        return "user".equalsIgnoreCase(last.author()) && Objects.equals(invocationId, last.invocationId());
    }

    private boolean isDuplicateUserEvent(Event candidate) {
        if (events.isEmpty() || !"user".equalsIgnoreCase(candidate.author())) {
            return false;
        }
        Event last = events.get(events.size() - 1);
        if (!"user".equalsIgnoreCase(last.author())) {
            return false;
        }
        if (candidate.invocationId() != null
                && !candidate.invocationId().isBlank()
                && candidate.invocationId().equals(last.invocationId())) {
            return Objects.equals(candidate.content(), last.content());
        }
        return false;
    }

    private static String serializeResultMap(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }
}
