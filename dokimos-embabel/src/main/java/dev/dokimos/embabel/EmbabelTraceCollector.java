package dev.dokimos.embabel;

import com.embabel.agent.api.event.AgentProcessCompletedEvent;
import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.api.event.ToolCallRequestEvent;
import com.embabel.agent.api.event.ToolCallResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates an Embabel agent's tool calls into a Dokimos {@link AgentTrace}.
 *
 * <p>Embabel reports tool activity only through per-event {@link AgenticEventListener}
 * callbacks during a run, never as a return value. A single collector therefore observes
 * one agent run: register it on the run's {@code ProcessOptions} (see
 * {@link EmbabelSupport#attach}), execute the agent, then read the trace:
 *
 * <pre>{@code
 * com.embabel.agent.core.ProcessOptions options = new com.embabel.agent.core.ProcessOptions();
 * EmbabelTraceCollector collector = new EmbabelTraceCollector();
 * options = options.withListener(collector);
 *
 * AgentInvocation<MyResult> inv = AgentInvocation.builder(platform)
 *         .options(options)
 *         .build(MyResult.class);
 * inv.invoke(userInput);
 *
 * AgentTrace trace = collector.trace();
 * EvalTestCase tc = trace.toTestCase(userInput, EmbabelSupport.toToolDefinitions(collector));
 * }</pre>
 *
 * <p>The collector maps every {@link ToolCallResponseEvent} to one {@link ToolCall}, reading
 * the tool name and raw arguments from the back-referenced {@link ToolCallRequestEvent} and the
 * tool result from the response event's {@code kotlin.Result}. The {@code kotlin.Result} accessor
 * is name-mangled and cannot be called from Java source, so it is read through a single reflective
 * helper that unwraps the boxed result and never throws.
 *
 * <p><strong>Silent-drop contract.</strong> A tool call that is requested but never produces a
 * {@link ToolCallResponseEvent} (for example, a tool that fails mid-run) is absent from the trace,
 * because only response events map to a {@link ToolCall}. This is intentional.
 *
 * <p>The collector is not thread-safe and is meant for a single agent run. To reuse one instance
 * across runs, call {@link #reset()} between runs.
 */
public final class EmbabelTraceCollector implements AgenticEventListener {

    private static final ObjectMapper TOOL_ARG_MAPPER = new ObjectMapper();

    private final List<ToolCall> toolCalls = new ArrayList<>();
    private final Set<String> observedToolNames = new LinkedHashSet<>();
    private String finalResponse;

    /**
     * Creates a fresh collector with no captured events.
     */
    public EmbabelTraceCollector() {}

    /**
     * Receives every {@link AgentProcessEvent} for the run and dispatches by concrete type.
     *
     * <p>This is the single surface that reads Embabel event fields: a pre-1.0 field rename is a
     * one-method fix. {@link ToolCallResponseEvent}s are mapped to {@link ToolCall}s and
     * {@link AgentProcessCompletedEvent} sets the final response; all other events are ignored.
     *
     * @param event the process event, never null in practice
     */
    @Override
    public void onProcessEvent(AgentProcessEvent event) {
        if (event instanceof ToolCallResponseEvent response) {
            recordToolCall(response);
        } else if (event instanceof AgentProcessCompletedEvent completed) {
            Object result = completed.getResult();
            finalResponse = result != null ? String.valueOf(result) : null;
        }
    }

    private void recordToolCall(ToolCallResponseEvent response) {
        ToolCallRequestEvent request = response.getRequest();
        if (request == null) {
            return;
        }
        String name = request.getTool();
        if (name == null || name.isBlank()) {
            return;
        }

        ToolCall.Builder builder = ToolCall.builder().name(name).arguments(parseArguments(request.getToolInput()));

        Object result = invokeMangledResult(response);
        String resultValue = resultValueOrNull(result);
        if (resultValue != null) {
            builder.result(resultValue);
        } else {
            String error = resultErrorOrNull(result);
            if (error != null) {
                builder.metadata("error", error);
            }
        }

        String correlationId = request.getCorrelationId();
        if (correlationId != null) {
            builder.metadata("correlationId", correlationId);
        }
        Duration runningTime = response.getRunningTime();
        if (runningTime != null) {
            builder.metadata("runningTimeMillis", runningTime.toMillis());
        }
        if (request.getAction() != null && request.getAction().getName() != null) {
            builder.metadata("action", request.getAction().getName());
        }

        toolCalls.add(builder.build());
        observedToolNames.add(name);
    }

    /**
     * Returns the tool calls captured so far, in execution order.
     *
     * @return an immutable copy of the captured tool calls
     */
    public List<ToolCall> toolCalls() {
        return List.copyOf(toolCalls);
    }

    /**
     * Returns the distinct tool names observed during the run, in first-seen order.
     *
     * <p>Used by {@link EmbabelSupport#toToolDefinitions(EmbabelTraceCollector)} to synthesize
     * tool definitions when the framework does not expose the original tool contracts.
     *
     * @return an immutable copy of the observed tool names
     */
    public List<String> observedToolNames() {
        return List.copyOf(observedToolNames);
    }

    /**
     * Materializes the captured run into an {@link AgentTrace}.
     *
     * <p>The final response is taken from the {@link AgentProcessCompletedEvent} if one was seen;
     * otherwise it is left null. With no events seen, an empty trace is returned. This method never
     * throws.
     *
     * @return an agent trace carrying the captured tool calls in order
     */
    public AgentTrace trace() {
        AgentTrace.Builder builder = AgentTrace.builder().toolCalls(List.copyOf(toolCalls));
        if (finalResponse != null) {
            builder.finalResponse(finalResponse);
        }
        return builder.build();
    }

    /**
     * Clears all captured state so this collector can observe a fresh run.
     *
     * <p>Call between runs when reusing one collector instance to prevent tool calls from a prior
     * run leaking into the next trace.
     */
    public void reset() {
        toolCalls.clear();
        observedToolNames.clear();
        finalResponse = null;
    }

    /**
     * Defensively parses a tool's raw JSON argument string into a map.
     *
     * <p>Returns an empty map for null, blank, or malformed input and never throws, so a single bad
     * argument string cannot fail the whole trace. This is a deliberate per-module copy of the same
     * convention used elsewhere in Dokimos rather than a shared helper.
     */
    private static Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = TOOL_ARG_MAPPER.readValue(argumentsJson, new TypeReference<>() {});
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Invokes the name-mangled {@code ToolCallResponseEvent.getResult-d1pmJ48()} accessor, which
     * returns a boxed {@code kotlin.Result} and cannot be referenced from Java source. Returns the
     * boxed result object, or null if it cannot be read; never throws.
     */
    private static Object invokeMangledResult(ToolCallResponseEvent response) {
        for (Method method : response.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getName().startsWith("getResult-")) {
                try {
                    method.setAccessible(true);
                    return method.invoke(response);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * The single reflective {@code kotlin.Result} unwrap helper (decision O1).
     *
     * <p>{@code kotlin.Result} is an inline value class; its public Java surface is the mangled
     * {@code -impl} statics plus the instance {@code unbox-impl()}. There is no public
     * {@code getOrNull} on the boxed instance and none in {@code kotlin.ResultKt} at this version, so
     * we unbox the raw payload, treat a failure payload as no value, and stringify a present success
     * value. Returns null for a failure or absent value and never throws.
     */
    private static String resultValueOrNull(Object kotlinResult) {
        Object payload = unboxPayload(kotlinResult);
        if (payload == null || isFailurePayload(payload)) {
            return null;
        }
        return String.valueOf(payload);
    }

    /**
     * Reads the failure message from a boxed {@code kotlin.Result}, if it carries one. Returns null
     * for a success or when the exception cannot be read; never throws.
     */
    private static String resultErrorOrNull(Object kotlinResult) {
        Object payload = unboxPayload(kotlinResult);
        if (payload == null || !isFailurePayload(payload)) {
            return null;
        }
        try {
            Class<?> result = Class.forName("kotlin.Result");
            Method exceptionOrNull = result.getMethod("exceptionOrNull-impl", Object.class);
            Object throwable = exceptionOrNull.invoke(null, payload);
            if (throwable instanceof Throwable t) {
                return t.getMessage() != null ? t.getMessage() : t.getClass().getName();
            }
        } catch (ReflectiveOperationException ignored) {
            // never throw out of the collector
        }
        return null;
    }

    /**
     * Calls the boxed {@code kotlin.Result}'s {@code unbox-impl()} to obtain its raw payload (the
     * success value, or a {@code Result.Failure} wrapper for a failure). Returns null if the value
     * cannot be unboxed; never throws.
     */
    private static Object unboxPayload(Object kotlinResult) {
        if (kotlinResult == null) {
            return null;
        }
        try {
            Method unbox = kotlinResult.getClass().getMethod("unbox-impl");
            return unbox.invoke(kotlinResult);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Tests whether an unboxed {@code kotlin.Result} payload represents a failure via the static
     * {@code isFailure-impl(Object)} accessor. Returns false if it cannot be determined; never throws.
     */
    private static boolean isFailurePayload(Object payload) {
        try {
            Class<?> result = Class.forName("kotlin.Result");
            Method isFailure = result.getMethod("isFailure-impl", Object.class);
            Object value = isFailure.invoke(null, payload);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
