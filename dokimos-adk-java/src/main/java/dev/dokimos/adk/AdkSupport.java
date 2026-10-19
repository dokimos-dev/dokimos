package dev.dokimos.adk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GroundingChunk;
import com.google.genai.types.GroundingChunkRetrievedContext;
import com.google.genai.types.GroundingChunkWeb;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.RagChunk;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import dev.dokimos.core.AsyncTask;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.MeasuredTask;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.Task;
import dev.dokimos.core.TaskResult;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.conversation.ConversationalApplication;
import dev.dokimos.core.conversation.Message;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridges Google Agent Development Kit (ADK) for Java types ({@link BaseAgent},
 * {@link LlmAgent}, {@link Runner}, {@link InMemoryRunner}, {@link BaseLlm},
 * {@link Event}, {@link Session}, {@link BaseTool}, {@link FunctionDeclaration},
 * {@link GroundingMetadata}, and {@link AdkTraceCollector})
 * to Dokimos evaluation types ({@link JudgeLM}, {@link Task}, {@link AsyncTask},
 * {@link MeasuredTask}, {@link TaskResult}, {@link AgentTrace},
 * {@link ToolCall}, {@link ToolDefinition}, {@link ConversationTrajectory},
 * {@link ConversationalApplication}, and {@link CallMetrics}).
 */
public final class AdkSupport {

    /** Default dataset input key read by tasks constructed in this class. */
    public static final String INPUT_KEY = "input";

    /** Default output key populated by tasks constructed in this class. */
    public static final String OUTPUT_KEY = "output";

    /** Default output key populated with retrieved context when applicable. */
    public static final String CONTEXT_KEY = "context";

    /** Default user ID used when creating isolated evaluation sessions. */
    public static final String DEFAULT_USER_ID = "dokimos-eval";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AdkSupport() {}

    // -------------------------------------------------------------------------
    // Judge adapters
    // -------------------------------------------------------------------------

    /**
     * Exposes an ADK {@link BaseLlm} (such as {@code Gemini}) as a Dokimos {@link JudgeLM}.
     *
     * @param llm the ADK LLM model (must not be null)
     * @return a JudgeLM backed by the model
     */
    public static JudgeLM asJudge(BaseLlm llm) {
        Objects.requireNonNull(llm, "llm must not be null");
        return prompt -> generateText(llm, prompt);
    }

    /**
     * Exposes an ADK {@link Runner} as a Dokimos {@link JudgeLM}. Each judge call runs in an
     * isolated session so prompts do not share state.
     *
     * @param runner the ADK runner (must not be null)
     * @return a JudgeLM backed by the runner
     */
    public static JudgeLM asJudge(Runner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        return prompt -> {
            List<Event> events = runEvents(runner, prompt != null ? prompt : "");
            return toAgentTrace(events).finalResponse();
        };
    }

    /**
     * Exposes an ADK {@link BaseAgent} as a Dokimos {@link JudgeLM} using an
     * {@link InMemoryRunner}. Each judge call runs in an isolated session.
     *
     * @param agent the ADK agent (must not be null)
     * @return a JudgeLM backed by the agent
     */
    public static JudgeLM asJudge(BaseAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        return asJudge(new InMemoryRunner(agent));
    }

    // -------------------------------------------------------------------------
    // Synchronous tasks
    // -------------------------------------------------------------------------

    /**
     * Creates a simple {@link Task} that calls an ADK {@link BaseLlm} with the example's
     * {@code input} and stores the text response under {@code output}.
     *
     * @param llm the ADK LLM model (must not be null)
     * @return a synchronous Task
     */
    public static Task simpleTask(BaseLlm llm) {
        return simpleTask(llm, OUTPUT_KEY);
    }

    /**
     * Creates a simple {@link Task} that calls an ADK {@link BaseLlm} with the example's
     * {@code input} and stores the text response under {@code outputKey}.
     *
     * @param llm       the ADK LLM model (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @return a synchronous Task
     */
    public static Task simpleTask(BaseLlm llm, String outputKey) {
        Objects.requireNonNull(llm, "llm must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return example -> {
            String response = generateText(llm, extractInput(example));
            return Map.of(outputKey, response);
        };
    }

    /**
     * Creates a simple {@link Task} that runs an ADK {@link Runner} with the example's
     * {@code input} and stores the final response under {@code output}.
     *
     * @param runner the ADK runner (must not be null)
     * @return a synchronous Task
     */
    public static Task simpleTask(Runner runner) {
        return simpleTask(runner, OUTPUT_KEY);
    }

    /**
     * Creates a simple {@link Task} that runs an ADK {@link Runner} with the example's
     * {@code input} and stores the final response under {@code outputKey}.
     *
     * @param runner    the ADK runner (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @return a synchronous Task
     */
    public static Task simpleTask(Runner runner, String outputKey) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return example -> {
            List<Event> events = runEvents(runner, extractInput(example));
            String response = toAgentTrace(events).finalResponse();
            return Map.of(outputKey, response);
        };
    }

    /**
     * Creates a simple {@link Task} that runs an ADK {@link BaseAgent} via an
     * {@link InMemoryRunner} and stores the final response under {@code output}.
     *
     * @param agent the ADK agent (must not be null)
     * @return a synchronous Task
     */
    public static Task simpleTask(BaseAgent agent) {
        return simpleTask(agent, OUTPUT_KEY);
    }

    /**
     * Creates a simple {@link Task} that runs an ADK {@link BaseAgent} via an
     * {@link InMemoryRunner} and stores the final response under {@code outputKey}.
     *
     * @param agent     the ADK agent (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @return a synchronous Task
     */
    public static Task simpleTask(BaseAgent agent, String outputKey) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return simpleTask(new InMemoryRunner(agent), outputKey);
    }

    /**
     * Creates a {@link Task} for evaluating an ADK {@link Runner} with agent evaluators.
     * Each example runs in an isolated session, is converted to an {@link AgentTrace},
     * and returns {@link AgentTrace#toOutputMap()} containing {@code output},
     * {@code toolCalls}, and {@code reasoningSteps}.
     *
     * @param runner the ADK runner (must not be null)
     * @return a Task producing the agent trace output map
     */
    public static Task agentTask(Runner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        return example -> toAgentTrace(runner, extractInput(example)).toOutputMap();
    }

    /**
     * Creates a {@link Task} for evaluating an ADK {@link BaseAgent} with agent evaluators
     * using an {@link InMemoryRunner}.
     *
     * @param agent the ADK agent (must not be null)
     * @return a Task producing the agent trace output map
     */
    public static Task agentTask(BaseAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        return agentTask(new InMemoryRunner(agent));
    }

    /**
     * Creates a RAG evaluation {@link Task} that runs an ADK {@link Runner} and populates both
     * {@code output} (the final response) and {@code context} (the retrieved context strings
     * extracted via {@link #extractTexts(List, String...)} from {@link GroundingMetadata} and/or
     * tool responses).
     *
     * @param runner the ADK runner (must not be null)
     * @return a Task suitable for RAG evaluation (e.g., with {@code FaithfulnessEvaluator})
     */
    public static Task ragTask(Runner runner) {
        return ragTask(runner, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY, List.of());
    }

    /**
     * Creates a RAG evaluation {@link Task} that runs an ADK {@link Runner} and extracts
     * {@code context} from {@link GroundingMetadata} and the specified {@code retrievalToolNames}.
     *
     * @param runner             the ADK runner (must not be null)
     * @param retrievalToolNames names of retrieval tools whose {@link FunctionResponse}s carry context
     * @return a Task suitable for RAG evaluation
     */
    public static Task ragTask(Runner runner, List<String> retrievalToolNames) {
        return ragTask(runner, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY, retrievalToolNames);
    }

    /**
     * Creates a RAG evaluation {@link Task} with custom input, output, and context keys.
     *
     * @param runner     the ADK runner (must not be null)
     * @param inputKey   the dataset input key to read (must not be null)
     * @param outputKey  the output map key for the response (must not be null)
     * @param contextKey the output map key for the retrieved context list (must not be null)
     * @return a Task suitable for RAG evaluation
     */
    public static Task ragTask(Runner runner, String inputKey, String outputKey, String contextKey) {
        return ragTask(runner, inputKey, outputKey, contextKey, List.of());
    }

    /**
     * Creates a RAG evaluation {@link Task} with custom input, output, and context keys and
     * an explicit list of retrieval tool names.
     *
     * @param runner             the ADK runner (must not be null)
     * @param inputKey           the dataset input key to read (must not be null)
     * @param outputKey          the output map key for the response (must not be null)
     * @param contextKey         the output map key for the retrieved context list (must not be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context (may be null or empty)
     * @return a Task suitable for RAG evaluation
     */
    public static Task ragTask(
            Runner runner, String inputKey, String outputKey, String contextKey, List<String> retrievalToolNames) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(inputKey, "inputKey must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        Objects.requireNonNull(contextKey, "contextKey must not be null");
        String[] toolNames = toToolNameArray(retrievalToolNames);
        return example -> {
            List<Event> events = runEvents(runner, extractInput(example, inputKey));
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put(outputKey, toAgentTrace(events).finalResponse());
            outputs.put(contextKey, extractTexts(events, toolNames));
            return Map.copyOf(outputs);
        };
    }

    /**
     * Creates a RAG evaluation {@link Task} that runs an ADK {@link BaseAgent} via an
     * {@link InMemoryRunner} and populates both {@code output} and {@code context}.
     *
     * @param agent the ADK agent (must not be null)
     * @return a Task suitable for RAG evaluation
     */
    public static Task ragTask(BaseAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        return ragTask(new InMemoryRunner(agent), INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY, List.of());
    }

    /**
     * Creates a RAG evaluation {@link Task} that runs an ADK {@link BaseAgent} via an
     * {@link InMemoryRunner} and extracts {@code context} from the specified {@code retrievalToolNames}.
     *
     * @param agent              the ADK agent (must not be null)
     * @param retrievalToolNames names of retrieval tools whose {@link FunctionResponse}s carry context
     * @return a Task suitable for RAG evaluation
     */
    public static Task ragTask(BaseAgent agent, List<String> retrievalToolNames) {
        Objects.requireNonNull(agent, "agent must not be null");
        return ragTask(new InMemoryRunner(agent), INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY, retrievalToolNames);
    }

    /**
     * Creates a RAG evaluation {@link Task} for an ADK {@link BaseAgent} with custom key names.
     *
     * @param agent      the ADK agent (must not be null)
     * @param inputKey   the dataset input key to read (must not be null)
     * @param outputKey  the output map key for the response (must not be null)
     * @param contextKey the output map key for the retrieved context list (must not be null)
     * @return a Task suitable for RAG evaluation
     */
    public static Task ragTask(BaseAgent agent, String inputKey, String outputKey, String contextKey) {
        Objects.requireNonNull(agent, "agent must not be null");
        return ragTask(new InMemoryRunner(agent), inputKey, outputKey, contextKey, List.of());
    }

    /**
     * Creates a RAG evaluation {@link Task} for an ADK {@link BaseAgent} with custom key names
     * and an explicit list of retrieval tool names.
     *
     * @param agent              the ADK agent (must not be null)
     * @param inputKey           the dataset input key to read (must not be null)
     * @param outputKey          the output map key for the response (must not be null)
     * @param contextKey         the output map key for the retrieved context list (must not be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return a Task suitable for RAG evaluation
     */
    public static Task ragTask(
            BaseAgent agent, String inputKey, String outputKey, String contextKey, List<String> retrievalToolNames) {
        Objects.requireNonNull(agent, "agent must not be null");
        return ragTask(new InMemoryRunner(agent), inputKey, outputKey, contextKey, retrievalToolNames);
    }

    // -------------------------------------------------------------------------
    // Asynchronous tasks
    // -------------------------------------------------------------------------

    /**
     * Creates an {@link AsyncTask} backed by an ADK {@link BaseLlm}'s reactive stream,
     * storing the text response under {@code output}.
     *
     * @param llm the ADK LLM model (must not be null)
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(BaseLlm llm) {
        return asyncTask(llm, OUTPUT_KEY);
    }

    /**
     * Creates an {@link AsyncTask} backed by an ADK {@link BaseLlm}'s reactive stream,
     * storing the text response under {@code outputKey}.
     *
     * @param llm       the ADK LLM model (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(BaseLlm llm, String outputKey) {
        Objects.requireNonNull(llm, "llm must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return example -> generateResponsesAsync(llm, extractInput(example))
                .thenApply(responses -> TaskResult.of(Map.of(outputKey, extractTextFromLlmResponses(responses))));
    }

    /**
     * Creates an {@link AsyncTask} that runs an ADK {@link BaseLlm} on the supplied
     * {@link Executor}, storing the text response under {@code output}.
     *
     * @param llm      the ADK LLM model (must not be null)
     * @param executor the executor to run calls on (must not be null)
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(BaseLlm llm, Executor executor) {
        return asyncTask(llm, OUTPUT_KEY, executor);
    }

    /**
     * Creates an {@link AsyncTask} that runs an ADK {@link BaseLlm} on the supplied
     * {@link Executor}, storing the text response under {@code outputKey}.
     *
     * @param llm       the ADK LLM model (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @param executor  the executor to run calls on (must not be null)
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(BaseLlm llm, String outputKey, Executor executor) {
        Objects.requireNonNull(llm, "llm must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Task sync = simpleTask(llm, outputKey);
        return example -> CompletableFuture.supplyAsync(() -> TaskResult.of(sync.run(example)), executor);
    }

    /**
     * Creates an {@link AsyncTask} backed by an ADK {@link Runner}'s reactive
     * {@link Runner#runAsync} stream, storing the final response under {@code output}.
     *
     * @param runner the ADK runner (must not be null)
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(Runner runner) {
        return asyncTask(runner, OUTPUT_KEY);
    }

    /**
     * Creates an {@link AsyncTask} backed by an ADK {@link Runner}'s reactive
     * {@link Runner#runAsync} stream, storing the final response under {@code outputKey}.
     *
     * @param runner    the ADK runner (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(Runner runner, String outputKey) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return example -> runEventsAsync(runner, extractInput(example))
                .thenApply(events ->
                        TaskResult.of(Map.of(outputKey, toAgentTrace(events).finalResponse())));
    }

    /**
     * Creates an {@link AsyncTask} that runs an ADK {@link Runner} on the supplied
     * {@link Executor}, storing the final response under {@code output}.
     *
     * @param runner   the ADK runner (must not be null)
     * @param executor the executor to run calls on (must not be null)
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(Runner runner, Executor executor) {
        return asyncTask(runner, OUTPUT_KEY, executor);
    }

    /**
     * Creates an {@link AsyncTask} that runs an ADK {@link Runner} on the supplied
     * {@link Executor}, storing the final response under {@code outputKey}.
     *
     * @param runner    the ADK runner (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @param executor  the executor to run calls on (must not be null)
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(Runner runner, String outputKey, Executor executor) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Task sync = simpleTask(runner, outputKey);
        return example -> CompletableFuture.supplyAsync(() -> TaskResult.of(sync.run(example)), executor);
    }

    /**
     * Creates an {@link AsyncTask} backed by an ADK {@link BaseAgent} via an
     * {@link InMemoryRunner}, storing the final response under {@code output}.
     *
     * @param agent the ADK agent (must not be null)
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(BaseAgent agent) {
        return asyncTask(agent, OUTPUT_KEY);
    }

    /**
     * Creates an {@link AsyncTask} backed by an ADK {@link BaseAgent} via an
     * {@link InMemoryRunner}, storing the final response under {@code outputKey}.
     *
     * @param agent     the ADK agent (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @return an AsyncTask
     */
    public static AsyncTask asyncTask(BaseAgent agent, String outputKey) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return asyncTask(new InMemoryRunner(agent), outputKey);
    }

    /**
     * Creates an {@link AsyncTask} for evaluating an ADK {@link Runner} with agent evaluators.
     * Bridges {@link Runner#runAsync} to a {@link CompletableFuture} and converts the resulting
     * events into {@link AgentTrace#toOutputMap()}.
     *
     * @param runner the ADK runner (must not be null)
     * @return an AsyncTask producing the agent trace output map
     */
    public static AsyncTask asyncAgentTask(Runner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        return example -> runEventsAsync(runner, extractInput(example))
                .thenApply(events -> TaskResult.of(toAgentTrace(events).toOutputMap()));
    }

    /**
     * Creates an {@link AsyncTask} for evaluating an ADK {@link Runner} on the supplied
     * {@link Executor}, producing {@link AgentTrace#toOutputMap()}.
     *
     * @param runner   the ADK runner (must not be null)
     * @param executor the executor to run calls on (must not be null)
     * @return an AsyncTask producing the agent trace output map
     */
    public static AsyncTask asyncAgentTask(Runner runner, Executor executor) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Task sync = agentTask(runner);
        return example -> CompletableFuture.supplyAsync(() -> TaskResult.of(sync.run(example)), executor);
    }

    /**
     * Creates an {@link AsyncTask} for evaluating an ADK {@link BaseAgent} with agent evaluators
     * using an {@link InMemoryRunner}.
     *
     * @param agent the ADK agent (must not be null)
     * @return an AsyncTask producing the agent trace output map
     */
    public static AsyncTask asyncAgentTask(BaseAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        return asyncAgentTask(new InMemoryRunner(agent));
    }

    /**
     * Creates an {@link AsyncTask} for RAG evaluation backed by an ADK {@link Runner}'s reactive
     * stream, populating {@code output} and {@code context}.
     *
     * @param runner             the ADK runner (must not be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return an AsyncTask suitable for RAG evaluation
     */
    public static AsyncTask asyncRagTask(Runner runner, String... retrievalToolNames) {
        return asyncRagTask(runner, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY, null, retrievalToolNames);
    }

    /**
     * Creates an {@link AsyncTask} for RAG evaluation that runs an ADK {@link Runner} on the
     * supplied {@link Executor}, populating {@code output} and {@code context}.
     *
     * @param runner             the ADK runner (must not be null)
     * @param executor           the executor to run calls on (must not be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return an AsyncTask suitable for RAG evaluation
     */
    public static AsyncTask asyncRagTask(Runner runner, Executor executor, String... retrievalToolNames) {
        Objects.requireNonNull(executor, "executor must not be null");
        return asyncRagTask(runner, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY, executor, retrievalToolNames);
    }

    /**
     * Creates an {@link AsyncTask} for RAG evaluation with custom key names and an optional
     * {@link Executor}. When {@code executor} is {@code null}, uses {@link Runner#runAsync}
     * directly.
     *
     * @param runner             the ADK runner (must not be null)
     * @param inputKey           the dataset input key to read (must not be null)
     * @param outputKey          the output map key for the response (must not be null)
     * @param contextKey         the output map key for the retrieved context list (must not be null)
     * @param executor           optional executor to run blocking calls on (may be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return an AsyncTask suitable for RAG evaluation
     */
    public static AsyncTask asyncRagTask(
            Runner runner,
            String inputKey,
            String outputKey,
            String contextKey,
            Executor executor,
            String... retrievalToolNames) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(inputKey, "inputKey must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        Objects.requireNonNull(contextKey, "contextKey must not be null");
        if (executor != null) {
            List<String> tools = retrievalToolNames != null ? Arrays.asList(retrievalToolNames) : List.of();
            Task sync = ragTask(runner, inputKey, outputKey, contextKey, tools);
            return example -> CompletableFuture.supplyAsync(() -> TaskResult.of(sync.run(example)), executor);
        }
        return example -> runEventsAsync(runner, extractInput(example, inputKey))
                .thenApply(events -> {
                    Map<String, Object> outputs = new LinkedHashMap<>();
                    outputs.put(outputKey, toAgentTrace(events).finalResponse());
                    outputs.put(contextKey, extractTexts(events, retrievalToolNames));
                    return TaskResult.of(Map.copyOf(outputs));
                });
    }

    /**
     * Creates an {@link AsyncTask} for RAG evaluation backed by an ADK {@link BaseAgent} via an
     * {@link InMemoryRunner}, populating {@code output} and {@code context}.
     *
     * @param agent              the ADK agent (must not be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return an AsyncTask suitable for RAG evaluation
     */
    public static AsyncTask asyncRagTask(BaseAgent agent, String... retrievalToolNames) {
        Objects.requireNonNull(agent, "agent must not be null");
        return asyncRagTask(new InMemoryRunner(agent), retrievalToolNames);
    }

    // -------------------------------------------------------------------------
    // Measured tasks & CallMetrics
    // -------------------------------------------------------------------------

    /**
     * Converts ADK / GenAI {@link GenerateContentResponseUsageMetadata} into a Dokimos
     * {@link CallMetrics} record, computing cost via the optional {@link PriceTable}.
     *
     * @param usage     the usage metadata (may be null)
     * @param latencyMs the measured wall-clock latency in milliseconds
     * @param modelId   the model identifier for cost lookup (may be null)
     * @param prices    the price table for cost lookup (may be null)
     * @return a populated CallMetrics
     */
    public static CallMetrics toCallMetrics(
            GenerateContentResponseUsageMetadata usage, long latencyMs, String modelId, PriceTable prices) {
        Integer tokensIn = null;
        Integer tokensOut = null;
        if (usage != null) {
            int p = usage.promptTokenCount().orElse(0);
            int c = usage.candidatesTokenCount().orElse(0);
            if (p > 0 || c > 0) {
                tokensIn = p;
                tokensOut = c;
            }
        }
        Double costUsd = (prices != null && modelId != null && tokensIn != null && tokensOut != null)
                ? prices.costUsd(modelId, tokensIn, tokensOut)
                : null;
        return new CallMetrics(tokensIn, tokensOut, costUsd, latencyMs);
    }

    /**
     * Aggregates token usage across a list of ADK {@link Event}s into a single
     * {@link CallMetrics} record, summing {@code promptTokenCount} and
     * {@code candidatesTokenCount} across all non-partial events that carry usage metadata.
     *
     * @param events    the events emitted during an agent run (may be null)
     * @param latencyMs the measured wall-clock latency in milliseconds
     * @param modelId   the model identifier for cost lookup (may be null)
     * @param prices    the price table for cost lookup (may be null)
     * @return a populated CallMetrics
     */
    public static CallMetrics toCallMetrics(List<Event> events, long latencyMs, String modelId, PriceTable prices) {
        Integer tokensIn = null;
        Integer tokensOut = null;
        if (events != null && !events.isEmpty()) {
            int pSum = 0;
            int cSum = 0;
            for (Event event : nonPartialEvents(events)) {
                if (event.usageMetadata().isPresent()) {
                    GenerateContentResponseUsageMetadata u =
                            event.usageMetadata().get();
                    pSum += u.promptTokenCount().orElse(0);
                    cSum += u.candidatesTokenCount().orElse(0);
                }
            }
            if (pSum > 0 || cSum > 0) {
                tokensIn = pSum;
                tokensOut = cSum;
            }
        }
        Double costUsd = (prices != null && modelId != null && tokensIn != null && tokensOut != null)
                ? prices.costUsd(modelId, tokensIn, tokensOut)
                : null;
        return new CallMetrics(tokensIn, tokensOut, costUsd, latencyMs);
    }

    /**
     * Creates a {@link MeasuredTask} that calls an ADK {@link BaseLlm}, records latency,
     * token usage, and cost, and stores the text response under {@code output}.
     *
     * @param llm     the ADK LLM model (must not be null)
     * @param modelId the model identifier for cost lookup (may be null)
     * @param prices  the price table for cost lookup (may be null)
     * @return a MeasuredTask
     */
    public static MeasuredTask measuredTask(BaseLlm llm, String modelId, PriceTable prices) {
        return measuredTask(llm, OUTPUT_KEY, modelId, prices);
    }

    /**
     * Creates a {@link MeasuredTask} that calls an ADK {@link BaseLlm}, records latency,
     * token usage, and cost, and stores the text response under {@code outputKey}.
     *
     * @param llm       the ADK LLM model (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @param modelId   the model identifier for cost lookup (may be null)
     * @param prices    the price table for cost lookup (may be null)
     * @return a MeasuredTask
     */
    public static MeasuredTask measuredTask(BaseLlm llm, String outputKey, String modelId, PriceTable prices) {
        Objects.requireNonNull(llm, "llm must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return example -> {
            long startNanos = System.nanoTime();
            List<LlmResponse> responses = generateResponses(llm, extractInput(example));
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            String text = extractTextFromLlmResponses(responses);
            CallMetrics metrics = toCallMetricsFromLlmResponses(responses, latencyMs, modelId, prices);
            return new TaskResult(Map.of(outputKey, text), metrics);
        };
    }

    /**
     * Creates a {@link MeasuredTask} that runs an ADK {@link Runner}, records latency,
     * aggregated token usage across all emitted events, and cost, and stores the final
     * response under {@code output}.
     *
     * @param runner  the ADK runner (must not be null)
     * @param modelId the model identifier for cost lookup (may be null)
     * @param prices  the price table for cost lookup (may be null)
     * @return a MeasuredTask
     */
    public static MeasuredTask measuredTask(Runner runner, String modelId, PriceTable prices) {
        return measuredTask(runner, OUTPUT_KEY, modelId, prices);
    }

    /**
     * Creates a {@link MeasuredTask} that runs an ADK {@link Runner}, records latency,
     * aggregated token usage across all emitted events, and cost, and stores the final
     * response under {@code outputKey}.
     *
     * @param runner    the ADK runner (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @param modelId   the model identifier for cost lookup (may be null)
     * @param prices    the price table for cost lookup (may be null)
     * @return a MeasuredTask
     */
    public static MeasuredTask measuredTask(Runner runner, String outputKey, String modelId, PriceTable prices) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return example -> {
            long startNanos = System.nanoTime();
            List<Event> events = runEvents(runner, extractInput(example));
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            String text = toAgentTrace(events).finalResponse();
            CallMetrics metrics = toCallMetrics(events, latencyMs, modelId, prices);
            return new TaskResult(Map.of(outputKey, text), metrics);
        };
    }

    /**
     * Creates a {@link MeasuredTask} that runs an ADK {@link BaseAgent} via an
     * {@link InMemoryRunner}, recording latency, aggregated token usage, and cost.
     *
     * @param agent   the ADK agent (must not be null)
     * @param modelId the model identifier for cost lookup (may be null)
     * @param prices  the price table for cost lookup (may be null)
     * @return a MeasuredTask
     */
    public static MeasuredTask measuredTask(BaseAgent agent, String modelId, PriceTable prices) {
        Objects.requireNonNull(agent, "agent must not be null");
        return measuredTask(new InMemoryRunner(agent), OUTPUT_KEY, modelId, prices);
    }

    /**
     * Creates a {@link MeasuredTask} for evaluating an ADK {@link Runner} with agent
     * evaluators while also capturing {@link CallMetrics} (latency, aggregated token usage,
     * and cost). The task outputs {@link AgentTrace#toOutputMap()}.
     *
     * @param runner  the ADK runner (must not be null)
     * @param modelId the model identifier for cost lookup (may be null)
     * @param prices  the price table for cost lookup (may be null)
     * @return a MeasuredTask producing the agent trace output map and call metrics
     */
    public static MeasuredTask measuredAgentTask(Runner runner, String modelId, PriceTable prices) {
        Objects.requireNonNull(runner, "runner must not be null");
        return example -> {
            long startNanos = System.nanoTime();
            List<Event> events = runEvents(runner, extractInput(example));
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            Map<String, Object> outputs = toAgentTrace(events).toOutputMap();
            CallMetrics metrics = toCallMetrics(events, latencyMs, modelId, prices);
            return new TaskResult(outputs, metrics);
        };
    }

    /**
     * Creates a {@link MeasuredTask} for evaluating an ADK {@link BaseAgent} with agent
     * evaluators via an {@link InMemoryRunner} while capturing {@link CallMetrics}.
     *
     * @param agent   the ADK agent (must not be null)
     * @param modelId the model identifier for cost lookup (may be null)
     * @param prices  the price table for cost lookup (may be null)
     * @return a MeasuredTask producing the agent trace output map and call metrics
     */
    public static MeasuredTask measuredAgentTask(BaseAgent agent, String modelId, PriceTable prices) {
        Objects.requireNonNull(agent, "agent must not be null");
        return measuredAgentTask(new InMemoryRunner(agent), modelId, prices);
    }

    /**
     * Creates a measured {@link AsyncTask} backed by an ADK {@link BaseLlm}'s reactive stream,
     * storing the text response under {@code output} and attaching {@link CallMetrics}.
     *
     * @param llm     the ADK LLM model (must not be null)
     * @param modelId the model identifier for cost lookup (may be null)
     * @param prices  the price table for cost lookup (may be null)
     * @return an AsyncTask carrying CallMetrics in its TaskResult
     */
    public static AsyncTask measuredAsyncTask(BaseLlm llm, String modelId, PriceTable prices) {
        return measuredAsyncTask(llm, OUTPUT_KEY, modelId, prices);
    }

    /**
     * Creates a measured {@link AsyncTask} backed by an ADK {@link BaseLlm}'s reactive stream,
     * storing the text response under {@code outputKey} and attaching {@link CallMetrics}.
     *
     * @param llm       the ADK LLM model (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @param modelId   the model identifier for cost lookup (may be null)
     * @param prices    the price table for cost lookup (may be null)
     * @return an AsyncTask carrying CallMetrics in its TaskResult
     */
    public static AsyncTask measuredAsyncTask(BaseLlm llm, String outputKey, String modelId, PriceTable prices) {
        Objects.requireNonNull(llm, "llm must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return example -> {
            long startNanos = System.nanoTime();
            return generateResponsesAsync(llm, extractInput(example)).thenApply(responses -> {
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                String text = extractTextFromLlmResponses(responses);
                CallMetrics metrics = toCallMetricsFromLlmResponses(responses, latencyMs, modelId, prices);
                return new TaskResult(Map.of(outputKey, text), metrics);
            });
        };
    }

    /**
     * Creates a measured {@link AsyncTask} that runs an ADK {@link BaseLlm} on the supplied
     * {@link Executor}, storing the text response under {@code outputKey} and attaching
     * {@link CallMetrics}.
     *
     * @param llm       the ADK LLM model (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @param modelId   the model identifier for cost lookup (may be null)
     * @param prices    the price table for cost lookup (may be null)
     * @param executor  the executor to run calls on (must not be null)
     * @return an AsyncTask carrying CallMetrics in its TaskResult
     */
    public static AsyncTask measuredAsyncTask(
            BaseLlm llm, String outputKey, String modelId, PriceTable prices, Executor executor) {
        Objects.requireNonNull(llm, "llm must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        MeasuredTask sync = measuredTask(llm, outputKey, modelId, prices);
        return example -> CompletableFuture.supplyAsync(() -> sync.run(example), executor);
    }

    /**
     * Creates a measured {@link AsyncTask} backed by an ADK {@link Runner}'s reactive stream,
     * storing the final response under {@code output} and attaching {@link CallMetrics}.
     *
     * @param runner  the ADK runner (must not be null)
     * @param modelId the model identifier for cost lookup (may be null)
     * @param prices  the price table for cost lookup (may be null)
     * @return an AsyncTask carrying CallMetrics in its TaskResult
     */
    public static AsyncTask measuredAsyncTask(Runner runner, String modelId, PriceTable prices) {
        return measuredAsyncTask(runner, OUTPUT_KEY, modelId, prices);
    }

    /**
     * Creates a measured {@link AsyncTask} backed by an ADK {@link Runner}'s reactive stream,
     * storing the final response under {@code outputKey} and attaching {@link CallMetrics}.
     *
     * @param runner    the ADK runner (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @param modelId   the model identifier for cost lookup (may be null)
     * @param prices    the price table for cost lookup (may be null)
     * @return an AsyncTask carrying CallMetrics in its TaskResult
     */
    public static AsyncTask measuredAsyncTask(Runner runner, String outputKey, String modelId, PriceTable prices) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        return example -> {
            long startNanos = System.nanoTime();
            return runEventsAsync(runner, extractInput(example)).thenApply(events -> {
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                String text = toAgentTrace(events).finalResponse();
                CallMetrics metrics = toCallMetrics(events, latencyMs, modelId, prices);
                return new TaskResult(Map.of(outputKey, text), metrics);
            });
        };
    }

    /**
     * Creates a measured {@link AsyncTask} that runs an ADK {@link Runner} on the supplied
     * {@link Executor}, storing the final response under {@code outputKey} and attaching
     * {@link CallMetrics}.
     *
     * @param runner    the ADK runner (must not be null)
     * @param outputKey the key under which the response is stored in the output map
     * @param modelId   the model identifier for cost lookup (may be null)
     * @param prices    the price table for cost lookup (may be null)
     * @param executor  the executor to run calls on (must not be null)
     * @return an AsyncTask carrying CallMetrics in its TaskResult
     */
    public static AsyncTask measuredAsyncTask(
            Runner runner, String outputKey, String modelId, PriceTable prices, Executor executor) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        MeasuredTask sync = measuredTask(runner, outputKey, modelId, prices);
        return example -> CompletableFuture.supplyAsync(() -> sync.run(example), executor);
    }

    /**
     * Creates a measured {@link AsyncTask} for evaluating an ADK {@link Runner} with agent
     * evaluators while capturing {@link CallMetrics}.
     *
     * @param runner  the ADK runner (must not be null)
     * @param modelId the model identifier for cost lookup (may be null)
     * @param prices  the price table for cost lookup (may be null)
     * @return an AsyncTask producing the agent trace output map and call metrics
     */
    public static AsyncTask measuredAsyncAgentTask(Runner runner, String modelId, PriceTable prices) {
        Objects.requireNonNull(runner, "runner must not be null");
        return example -> {
            long startNanos = System.nanoTime();
            return runEventsAsync(runner, extractInput(example)).thenApply(events -> {
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                Map<String, Object> outputs = toAgentTrace(events).toOutputMap();
                CallMetrics metrics = toCallMetrics(events, latencyMs, modelId, prices);
                return new TaskResult(outputs, metrics);
            });
        };
    }

    /**
     * Creates a {@link MeasuredTask} for RAG evaluation that runs an ADK {@link Runner},
     * populates {@code output} and {@code context}, and captures {@link CallMetrics}
     * (latency, aggregated token usage, and optional cost).
     *
     * @param runner             the ADK runner (must not be null)
     * @param modelId            the model identifier for cost lookup (may be null)
     * @param prices             the price table for cost lookup (may be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return a MeasuredTask suitable for RAG evaluation
     */
    public static MeasuredTask measuredRagTask(
            Runner runner, String modelId, PriceTable prices, String... retrievalToolNames) {
        return measuredRagTask(runner, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY, modelId, prices, retrievalToolNames);
    }

    /**
     * Creates a {@link MeasuredTask} for RAG evaluation with custom key names.
     *
     * @param runner             the ADK runner (must not be null)
     * @param inputKey           the dataset input key to read (must not be null)
     * @param outputKey          the output map key for the response (must not be null)
     * @param contextKey         the output map key for the retrieved context list (must not be null)
     * @param modelId            the model identifier for cost lookup (may be null)
     * @param prices             the price table for cost lookup (may be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return a MeasuredTask suitable for RAG evaluation
     */
    public static MeasuredTask measuredRagTask(
            Runner runner,
            String inputKey,
            String outputKey,
            String contextKey,
            String modelId,
            PriceTable prices,
            String... retrievalToolNames) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(inputKey, "inputKey must not be null");
        Objects.requireNonNull(outputKey, "outputKey must not be null");
        Objects.requireNonNull(contextKey, "contextKey must not be null");
        return example -> {
            long startNanos = System.nanoTime();
            List<Event> events = runEvents(runner, extractInput(example, inputKey));
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put(outputKey, toAgentTrace(events).finalResponse());
            outputs.put(contextKey, extractTexts(events, retrievalToolNames));
            CallMetrics metrics = toCallMetrics(events, latencyMs, modelId, prices);
            return new TaskResult(Map.copyOf(outputs), metrics);
        };
    }

    /**
     * Creates a {@link MeasuredTask} for RAG evaluation that runs an ADK {@link BaseAgent}
     * via an {@link InMemoryRunner}, populating {@code output}, {@code context}, and
     * {@link CallMetrics}.
     *
     * @param agent              the ADK agent (must not be null)
     * @param modelId            the model identifier for cost lookup (may be null)
     * @param prices             the price table for cost lookup (may be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return a MeasuredTask suitable for RAG evaluation
     */
    public static MeasuredTask measuredRagTask(
            BaseAgent agent, String modelId, PriceTable prices, String... retrievalToolNames) {
        Objects.requireNonNull(agent, "agent must not be null");
        return measuredRagTask(new InMemoryRunner(agent), modelId, prices, retrievalToolNames);
    }

    /**
     * Creates a measured {@link AsyncTask} for RAG evaluation backed by an ADK {@link Runner}'s
     * reactive stream, populating {@code output}, {@code context}, and {@link CallMetrics}.
     *
     * @param runner             the ADK runner (must not be null)
     * @param modelId            the model identifier for cost lookup (may be null)
     * @param prices             the price table for cost lookup (may be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return an AsyncTask carrying RAG outputs and CallMetrics
     */
    public static AsyncTask measuredAsyncRagTask(
            Runner runner, String modelId, PriceTable prices, String... retrievalToolNames) {
        Objects.requireNonNull(runner, "runner must not be null");
        return example -> {
            long startNanos = System.nanoTime();
            return runEventsAsync(runner, extractInput(example)).thenApply(events -> {
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                Map<String, Object> outputs = new LinkedHashMap<>();
                outputs.put(OUTPUT_KEY, toAgentTrace(events).finalResponse());
                outputs.put(CONTEXT_KEY, extractTexts(events, retrievalToolNames));
                CallMetrics metrics = toCallMetrics(events, latencyMs, modelId, prices);
                return new TaskResult(Map.copyOf(outputs), metrics);
            });
        };
    }

    /**
     * Creates a measured {@link AsyncTask} for RAG evaluation backed by an ADK {@link BaseAgent}
     * via an {@link InMemoryRunner}.
     *
     * @param agent              the ADK agent (must not be null)
     * @param modelId            the model identifier for cost lookup (may be null)
     * @param prices             the price table for cost lookup (may be null)
     * @param retrievalToolNames optional names of retrieval tools whose {@link FunctionResponse}s
     *                           carry retrieved context
     * @return an AsyncTask carrying RAG outputs and CallMetrics
     */
    public static AsyncTask measuredAsyncRagTask(
            BaseAgent agent, String modelId, PriceTable prices, String... retrievalToolNames) {
        Objects.requireNonNull(agent, "agent must not be null");
        return measuredAsyncRagTask(new InMemoryRunner(agent), modelId, prices, retrievalToolNames);
    }

    // -------------------------------------------------------------------------
    // Plugin trace collector & RAG context extraction
    // -------------------------------------------------------------------------

    /**
     * Registers the given {@link AdkTraceCollector} plugin on the supplied
     * {@link Runner.Builder} and returns the builder for chaining.
     *
     * @param runnerBuilder the runner builder to configure (must not be null)
     * @param collector     the trace collector plugin to register (must not be null)
     * @return the configured {@code runnerBuilder}
     */
    public static Runner.Builder attach(Runner.Builder runnerBuilder, AdkTraceCollector collector) {
        Objects.requireNonNull(runnerBuilder, "runnerBuilder must not be null");
        Objects.requireNonNull(collector, "collector must not be null");
        return runnerBuilder.plugins(collector);
    }

    /**
     * Creates a fresh {@link AdkTraceCollector} plugin, registers it on the supplied
     * {@link Runner.Builder}, and returns the collector so you can read {@link AdkTraceCollector#trace()}
     * after the run.
     *
     * @param runnerBuilder the runner builder to configure (must not be null)
     * @return the newly created and registered {@link AdkTraceCollector}
     */
    public static AdkTraceCollector attach(Runner.Builder runnerBuilder) {
        Objects.requireNonNull(runnerBuilder, "runnerBuilder must not be null");
        AdkTraceCollector collector = new AdkTraceCollector();
        runnerBuilder.plugins(collector);
        return collector;
    }

    /**
     * Returns the {@link ToolDefinition}s observed by an {@link AdkTraceCollector} during a run.
     *
     * @param collector the trace collector (must not be null)
     * @return an immutable list of tool definitions
     */
    public static List<ToolDefinition> toToolDefinitions(AdkTraceCollector collector) {
        Objects.requireNonNull(collector, "collector must not be null");
        return collector.toolDefinitions();
    }

    /**
     * Extracts retrieved context strings from a list of ADK {@link Event}s.
     * <p>
     * Inspects both:
     * <ol>
     *   <li>{@link Event#groundingMetadata()} ({@link GroundingChunkRetrievedContext#text()},
     *       {@link RagChunk#text()}, or {@link GroundingChunkWeb#title()}/{@code uri()}) for
     *       agents using built-in Gemini Grounding or Vertex AI RAG.</li>
     *   <li>{@link Event#functionResponses()} for agents using retrieval tools (such as
     *       {@code VertexAiRagRetrieval} or custom search {@code FunctionTool}s). When
     *       {@code retrievalToolNames} is provided, only responses from those tools are
     *       extracted; when omitted, tool responses are extracted whenever they contain
     *       recognized context keys ({@code "response"}, {@code "context"}, {@code "contexts"},
     *       {@code "documents"}, {@code "chunks"}, {@code "results"}, {@code "text"},
     *       {@code "content"}) or when no {@link GroundingMetadata} chunks were present.</li>
     * </ol>
     *
     * @param events             the ADK events emitted during a run (may be null or empty)
     * @param retrievalToolNames optional names of retrieval tools to filter {@link FunctionResponse}s by
     * @return an immutable list of retrieved context strings in order
     */
    public static List<String> extractTexts(List<Event> events, String... retrievalToolNames) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<Event> cleanEvents = nonPartialEvents(events);
        List<String> contexts = new ArrayList<>();

        for (Event event : cleanEvents) {
            if (event.groundingMetadata().isPresent()) {
                contexts.addAll(extractTexts(event.groundingMetadata().get()));
            }
        }

        Set<String> allowedTools = new LinkedHashSet<>();
        if (retrievalToolNames != null) {
            Arrays.stream(retrievalToolNames)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(allowedTools::add);
        }

        boolean hasExplicitToolFilter = !allowedTools.isEmpty();
        boolean hadGroundingChunks = !contexts.isEmpty();

        for (Event event : cleanEvents) {
            List<FunctionResponse> responses = event.functionResponses();
            if (responses == null || responses.isEmpty()) {
                continue;
            }
            for (FunctionResponse response : responses) {
                if (response == null || response.response().isEmpty()) {
                    continue;
                }
                String toolName = response.name().orElse("");
                if (hasExplicitToolFilter && !allowedTools.contains(toolName)) {
                    continue;
                }
                Map<String, Object> responseMap = response.response().get();
                List<String> extracted =
                        extractContextStringsFromMap(responseMap, hasExplicitToolFilter || !hadGroundingChunks);
                contexts.addAll(extracted);
            }
        }

        return List.copyOf(contexts);
    }

    /**
     * Extracts retrieved context strings from a single GenAI {@link GroundingMetadata}.
     *
     * @param metadata the grounding metadata (may be null)
     * @return an immutable list of retrieved context strings
     */
    public static List<String> extractTexts(GroundingMetadata metadata) {
        if (metadata == null || metadata.groundingChunks().isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (GroundingChunk chunk : metadata.groundingChunks().get()) {
            if (chunk == null) {
                continue;
            }
            if (chunk.retrievedContext().isPresent()) {
                GroundingChunkRetrievedContext rc = chunk.retrievedContext().get();
                if (rc.text().isPresent() && !rc.text().get().isBlank()) {
                    texts.add(rc.text().get());
                    continue;
                }
                if (rc.ragChunk().isPresent()
                        && rc.ragChunk().get().text().isPresent()
                        && !rc.ragChunk().get().text().get().isBlank()) {
                    texts.add(rc.ragChunk().get().text().get());
                    continue;
                }
                if (rc.title().isPresent() && !rc.title().get().isBlank()) {
                    texts.add(rc.title().get());
                }
            } else if (chunk.web().isPresent()) {
                GroundingChunkWeb web = chunk.web().get();
                String title = web.title().orElse("");
                String uri = web.uri().orElse("");
                if (!title.isBlank() && !uri.isBlank()) {
                    texts.add(title + " (" + uri + ")");
                } else if (!title.isBlank()) {
                    texts.add(title);
                } else if (!uri.isBlank()) {
                    texts.add(uri);
                }
            }
        }
        return List.copyOf(texts);
    }

    // -------------------------------------------------------------------------
    // Agent trace & tool-call extraction
    // -------------------------------------------------------------------------

    /**
     * Runs an ADK {@link Runner} for the given {@code input} in an isolated session and
     * converts the emitted {@link Event} stream into a Dokimos {@link AgentTrace}.
     *
     * @param runner the ADK runner (must not be null)
     * @param input  the user input message (must not be null)
     * @return the extracted AgentTrace
     */
    public static AgentTrace toAgentTrace(Runner runner, String input) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(input, "input must not be null");
        return toAgentTrace(runEvents(runner, input));
    }

    /**
     * Runs an ADK {@link BaseAgent} via an {@link InMemoryRunner} for the given {@code input}
     * and converts the emitted {@link Event} stream into a Dokimos {@link AgentTrace}.
     *
     * @param agent the ADK agent (must not be null)
     * @param input the user input message (must not be null)
     * @return the extracted AgentTrace
     */
    public static AgentTrace toAgentTrace(BaseAgent agent, String input) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(input, "input must not be null");
        return toAgentTrace(new InMemoryRunner(agent), input);
    }

    /**
     * Converts the events recorded in an ADK {@link Session} into a Dokimos {@link AgentTrace}.
     *
     * @param session the ADK session (may be null)
     * @return the extracted AgentTrace, or an empty trace if the session has no events
     */
    public static AgentTrace toAgentTrace(Session session) {
        if (session == null || session.events() == null) {
            return AgentTrace.builder().finalResponse("").build();
        }
        return toAgentTrace(session.events());
    }

    /**
     * Converts a sequence of ADK {@link Event}s into a Dokimos {@link AgentTrace},
     * folding {@link FunctionCall} and {@link FunctionResponse} pairs into {@link ToolCall}s,
     * extracting model thought parts as reasoning steps, and capturing the final assistant
     * response text.
     *
     * @param events the ADK events in chronological order (may be null or empty)
     * @return the extracted AgentTrace
     */
    public static AgentTrace toAgentTrace(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return AgentTrace.builder().finalResponse("").build();
        }

        List<Event> cleanEvents = nonPartialEvents(events);
        AgentTrace.Builder builder = AgentTrace.builder().toolCalls(toToolCalls(cleanEvents));

        for (Event event : cleanEvents) {
            for (String thought : extractThoughtTexts(event)) {
                builder.addReasoningStep(thought);
            }
        }

        String finalResponse = extractFinalResponse(cleanEvents);
        return builder.finalResponse(finalResponse).build();
    }

    /**
     * Extracts {@link ToolCall}s in chronological order from a sequence of ADK {@link Event}s.
     * <p>
     * Each model event containing {@link FunctionCall}s is matched against the
     * {@link FunctionResponse}s in the current turn window (up to the next event containing
     * {@link FunctionCall}s), first by call {@code id} when present and non-empty, and then
     * by tool {@code name} in order for calls without an {@code id}.
     *
     * @param events the ADK events in chronological order (may be null or empty)
     * @return the extracted tool calls in order, never null
     */
    public static List<ToolCall> toToolCalls(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<Event> cleanEvents = nonPartialEvents(events);
        List<ToolCall> result = new ArrayList<>();

        for (int i = 0; i < cleanEvents.size(); i++) {
            Event event = cleanEvents.get(i);
            List<FunctionCall> calls = event.functionCalls();
            if (calls == null || calls.isEmpty()) {
                continue;
            }

            // Collect FunctionResponses in this turn window: from the current event (if it
            // carries both calls and responses) and subsequent events up to the next event
            // that initiates new FunctionCalls.
            List<FunctionResponse> turnResponses = new ArrayList<>();
            if (event.functionResponses() != null && !event.functionResponses().isEmpty()) {
                turnResponses.addAll(event.functionResponses());
            }
            for (int j = i + 1; j < cleanEvents.size(); j++) {
                Event next = cleanEvents.get(j);
                if (next.functionCalls() != null && !next.functionCalls().isEmpty()) {
                    break;
                }
                if (next.functionResponses() != null
                        && !next.functionResponses().isEmpty()) {
                    turnResponses.addAll(next.functionResponses());
                }
            }

            result.addAll(matchCallsToResponses(calls, turnResponses));
        }

        return List.copyOf(result);
    }

    /**
     * Converts a single ADK / GenAI {@link FunctionCall} and its optional
     * {@link FunctionResponse} into a Dokimos {@link ToolCall}.
     * <p>
     * When {@code response} is non-null and carries a response map, the map is serialized
     * as compact JSON so both {@link ToolCall#resultAs(Class)} and
     * {@code ToolErrorEvaluator} (which inspects top-level {@code "error"} fields) work
     * seamlessly.
     *
     * @param call     the function call (must not be null)
     * @param response the matching function response (may be null)
     * @return the Dokimos ToolCall
     */
    public static ToolCall toToolCall(FunctionCall call, FunctionResponse response) {
        Objects.requireNonNull(call, "call must not be null");
        String result = serializeFunctionResponse(response);
        return toToolCall(call, result);
    }

    /**
     * Converts a single ADK / GenAI {@link FunctionCall} and a serialized result string
     * into a Dokimos {@link ToolCall}.
     *
     * @param call   the function call (must not be null)
     * @param result the tool execution result string (may be null)
     * @return the Dokimos ToolCall
     */
    public static ToolCall toToolCall(FunctionCall call, String result) {
        Objects.requireNonNull(call, "call must not be null");
        String name = call.name().orElse("");
        Map<String, Object> args = call.args().orElse(Map.of());
        return ToolCall.builder().name(name).arguments(args).result(result).build();
    }

    // -------------------------------------------------------------------------
    // Tool definitions
    // -------------------------------------------------------------------------

    /**
     * Extracts {@link ToolDefinition}s from an ADK {@link LlmAgent}'s configured tools.
     *
     * @param agent the ADK LlmAgent (may be null)
     * @return the Dokimos tool definitions, or an empty list
     */
    public static List<ToolDefinition> toToolDefinitions(LlmAgent agent) {
        if (agent == null) {
            return List.of();
        }
        List<BaseTool> tools = agent.tools().blockingGet();
        return toToolDefinitions(tools);
    }

    /**
     * Converts a list of ADK {@link BaseTool}s into Dokimos {@link ToolDefinition}s.
     *
     * @param tools the ADK tools (may be null or empty)
     * @return the Dokimos tool definitions, or an empty list
     */
    public static List<ToolDefinition> toToolDefinitions(List<? extends BaseTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .filter(Objects::nonNull)
                .map(AdkSupport::toToolDefinition)
                .toList();
    }

    /**
     * Converts a single ADK {@link BaseTool} into a Dokimos {@link ToolDefinition}.
     * Uses {@link BaseTool#declaration()} when present, falling back to
     * {@link BaseTool#name()} and {@link BaseTool#description()} with an empty object schema.
     *
     * @param tool the ADK tool (must not be null)
     * @return the Dokimos ToolDefinition
     */
    public static ToolDefinition toToolDefinition(BaseTool tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        Optional<FunctionDeclaration> declOpt = tool.declaration();
        if (declOpt.isPresent()) {
            FunctionDeclaration decl = declOpt.get();
            String name = decl.name().filter(s -> !s.isBlank()).orElse(tool.name());
            String description = decl.description().filter(s -> !s.isBlank()).orElse(tool.description());
            Map<String, Object> schema = extractSchemaFromDeclaration(decl);
            return ToolDefinition.of(name, description != null ? description : "", schema);
        }
        return ToolDefinition.of(
                tool.name(),
                tool.description() != null ? tool.description() : "",
                Map.of("type", "object", "properties", Map.of()));
    }

    /**
     * Converts an ADK / GenAI {@link FunctionDeclaration} into a Dokimos {@link ToolDefinition}.
     *
     * @param declaration the function declaration (must not be null)
     * @return the Dokimos ToolDefinition
     */
    public static ToolDefinition toToolDefinition(FunctionDeclaration declaration) {
        Objects.requireNonNull(declaration, "declaration must not be null");
        String name = declaration.name().orElse("");
        String description = declaration.description().orElse("");
        Map<String, Object> schema = extractSchemaFromDeclaration(declaration);
        return ToolDefinition.of(name, description, schema);
    }

    /**
     * Converts a GenAI {@link Schema} into a standard JSON Schema {@code Map<String, Object>}
     * with lowercase type names ({@code "object"}, {@code "string"}, {@code "integer"},
     * {@code "number"}, {@code "boolean"}, {@code "array"}) expected by Dokimos tool evaluators.
     *
     * @param schema the GenAI Schema (may be null)
     * @return a JSON Schema map, or an empty map if {@code schema} is null
     */
    public static Map<String, Object> toSchemaMap(Schema schema) {
        if (schema == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();

        if (schema.type().isPresent()) {
            Type type = schema.type().get();
            String typeName = type.toString().toLowerCase(Locale.ROOT);
            if (!typeName.isBlank() && !"type_unspecified".equals(typeName)) {
                map.put("type", typeName);
            }
        }

        if (schema.description().isPresent() && !schema.description().get().isBlank()) {
            map.put("description", schema.description().get());
        }

        if (schema.format().isPresent() && !schema.format().get().isBlank()) {
            map.put("format", schema.format().get());
        }

        if (schema.enum_().isPresent() && !schema.enum_().get().isEmpty()) {
            map.put("enum", List.copyOf(schema.enum_().get()));
        }

        if (schema.properties().isPresent() && !schema.properties().get().isEmpty()) {
            Map<String, Object> props = new LinkedHashMap<>();
            for (Map.Entry<String, Schema> entry : schema.properties().get().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    props.put(entry.getKey(), toSchemaMap(entry.getValue()));
                }
            }
            map.put("properties", props);
            map.putIfAbsent("type", "object");
        }

        if (schema.required().isPresent() && !schema.required().get().isEmpty()) {
            map.put("required", List.copyOf(schema.required().get()));
        }

        if (schema.items().isPresent()) {
            map.put("items", toSchemaMap(schema.items().get()));
            map.putIfAbsent("type", "array");
        }

        if (schema.anyOf().isPresent() && !schema.anyOf().get().isEmpty()) {
            List<Map<String, Object>> anyOfList = schema.anyOf().get().stream()
                    .filter(Objects::nonNull)
                    .map(AdkSupport::toSchemaMap)
                    .toList();
            map.put("anyOf", anyOfList);
        }

        return Map.copyOf(map);
    }

    // -------------------------------------------------------------------------
    // Multi-turn ConversationTrajectory & ConversationalApplication
    // -------------------------------------------------------------------------

    /**
     * Converts the events recorded in an ADK {@link Session} into a multi-turn
     * {@link ConversationTrajectory}.
     *
     * @param session the ADK session (may be null)
     * @return the extracted ConversationTrajectory
     */
    public static ConversationTrajectory toConversationTrajectory(Session session) {
        return toConversationTrajectory(session, "");
    }

    /**
     * Converts the events recorded in an ADK {@link Session} into a multi-turn
     * {@link ConversationTrajectory} with the given {@code scenario} description.
     *
     * @param session  the ADK session (may be null)
     * @param scenario the scenario description (may be null)
     * @return the extracted ConversationTrajectory
     */
    public static ConversationTrajectory toConversationTrajectory(Session session, String scenario) {
        if (session == null || session.events() == null) {
            return ConversationTrajectory.builder()
                    .scenario(scenario != null ? scenario : "")
                    .build();
        }
        return toConversationTrajectory(session.events(), scenario);
    }

    /**
     * Converts a chronological list of ADK {@link Event}s into a multi-turn
     * {@link ConversationTrajectory}.
     * <p>
     * User text events become {@link Message#user(String)} turns, and all subsequent model
     * and tool events up to the next user text event are folded into a single
     * {@link Message#assistant(String, List)} turn carrying the assistant's final reply for
     * that turn and all {@link ToolCall}s (with matched results) executed during that turn.
     *
     * @param events the ADK events in chronological order (may be null or empty)
     * @return the extracted ConversationTrajectory
     */
    public static ConversationTrajectory toConversationTrajectory(List<Event> events) {
        return toConversationTrajectory(events, "");
    }

    /**
     * Converts a chronological list of ADK {@link Event}s into a multi-turn
     * {@link ConversationTrajectory} with the given {@code scenario} description.
     *
     * @param events   the ADK events in chronological order (may be null or empty)
     * @param scenario the scenario description (may be null)
     * @return the extracted ConversationTrajectory
     */
    public static ConversationTrajectory toConversationTrajectory(List<Event> events, String scenario) {
        ConversationTrajectory.Builder builder =
                ConversationTrajectory.builder().scenario(scenario != null ? scenario : "");
        if (events == null || events.isEmpty()) {
            return builder.build();
        }

        List<Event> cleanEvents = nonPartialEvents(events);
        List<Event> currentAssistantTurnEvents = new ArrayList<>();

        for (Event event : cleanEvents) {
            if (isUserTextEvent(event)) {
                if (!currentAssistantTurnEvents.isEmpty()) {
                    appendAssistantTurnMessage(builder, currentAssistantTurnEvents);
                    currentAssistantTurnEvents.clear();
                }
                builder.userMessage(extractNonThoughtText(event));
            } else {
                currentAssistantTurnEvents.add(event);
            }
        }

        if (!currentAssistantTurnEvents.isEmpty()) {
            appendAssistantTurnMessage(builder, currentAssistantTurnEvents);
        }

        return builder.build();
    }

    /**
     * Adapts an ADK {@link Runner} as a Dokimos {@link ConversationalApplication} suitable
     * for driving multi-turn simulations with {@code ConversationSimulator}.
     * <p>
     * Each call to {@link ConversationalApplication#respond(ConversationTrajectory)} sends
     * the new user message(s) from the trajectory to a session managed by the runner and
     * returns a {@link Message#assistant(String, List)} containing both the assistant's text
     * response and any {@link ToolCall}s executed on that turn.
     *
     * @param runner the ADK runner (must not be null)
     * @return a ConversationalApplication backed by the runner
     */
    public static ConversationalApplication asConversationalApp(Runner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        String sessionId = UUID.randomUUID().toString();
        AtomicInteger processedUserMessages = new AtomicInteger(0);
        return trajectory -> {
            if (trajectory == null || trajectory.userMessages().isEmpty()) {
                return Message.assistant("");
            }
            List<Message> userMessages = trajectory.userMessages();
            int startIndex = processedUserMessages.get();
            if (startIndex >= userMessages.size()) {
                startIndex = userMessages.size() - 1;
            }
            List<Event> lastTurnEvents = List.of();
            for (int i = startIndex; i < userMessages.size(); i++) {
                lastTurnEvents = runEventsInSession(
                        runner, DEFAULT_USER_ID, sessionId, userMessages.get(i).content());
                processedUserMessages.set(i + 1);
            }
            AgentTrace trace = toAgentTrace(lastTurnEvents);
            return Message.assistant(trace.finalResponse(), trace.toolCalls());
        };
    }

    /**
     * Adapts an ADK {@link BaseAgent} as a Dokimos {@link ConversationalApplication} using
     * an {@link InMemoryRunner}.
     *
     * @param agent the ADK agent (must not be null)
     * @return a ConversationalApplication backed by the agent
     */
    public static ConversationalApplication asConversationalApp(BaseAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        return asConversationalApp(new InMemoryRunner(agent));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static final List<String> RECOGNIZED_CONTEXT_KEYS =
            List.of("response", "context", "contexts", "documents", "chunks", "results", "text", "content");

    private static String[] toToolNameArray(List<String> retrievalToolNames) {
        if (retrievalToolNames == null || retrievalToolNames.isEmpty()) {
            return new String[0];
        }
        return retrievalToolNames.toArray(String[]::new);
    }

    private static String extractInput(Example example) {
        return extractInput(example, INPUT_KEY);
    }

    private static String extractInput(Example example, String inputKey) {
        if (example == null) {
            return "";
        }
        String key = inputKey != null ? inputKey : INPUT_KEY;
        Object raw = example.inputs().get(key);
        if (raw != null) {
            return raw.toString();
        }
        if (INPUT_KEY.equals(key)) {
            String input = example.input();
            if (input != null) {
                return input;
            }
        }
        return "";
    }

    private static List<String> extractContextStringsFromMap(
            Map<String, Object> responseMap, boolean fallbackToWholeMap) {
        if (responseMap == null || responseMap.isEmpty()) {
            return List.of();
        }
        List<String> extracted = new ArrayList<>();
        for (String key : RECOGNIZED_CONTEXT_KEYS) {
            if (responseMap.containsKey(key)) {
                collectStrings(responseMap.get(key), extracted);
            }
        }
        if (!extracted.isEmpty()) {
            return extracted;
        }
        if (fallbackToWholeMap) {
            try {
                extracted.add(OBJECT_MAPPER.writeValueAsString(responseMap));
            } catch (Exception e) {
                extracted.add(responseMap.toString());
            }
        }
        return extracted;
    }

    private static void collectStrings(Object value, List<String> out) {
        if (value == null) {
            return;
        }
        if (value instanceof String str) {
            if (!str.isBlank()) {
                out.add(str);
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                collectStrings(item, out);
            }
        } else if (value instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text == null) {
                text = map.get("content");
            }
            if (text != null) {
                collectStrings(text, out);
            } else {
                try {
                    out.add(OBJECT_MAPPER.writeValueAsString(map));
                } catch (Exception e) {
                    out.add(map.toString());
                }
            }
        } else {
            String str = value.toString();
            if (!str.isBlank()) {
                out.add(str);
            }
        }
    }

    private static List<LlmResponse> generateResponses(BaseLlm llm, String prompt) {
        LlmRequest request = buildLlmRequest(prompt);
        Flowable<LlmResponse> flowable = llm.generateContent(request, false);
        if (flowable == null) {
            return List.of();
        }
        List<LlmResponse> responses = flowable.toList().blockingGet();
        return responses != null ? responses : List.of();
    }

    private static CompletableFuture<List<LlmResponse>> generateResponsesAsync(BaseLlm llm, String prompt) {
        try {
            LlmRequest request = buildLlmRequest(prompt);
            Flowable<LlmResponse> flowable = llm.generateContent(request, false);
            if (flowable == null) {
                return CompletableFuture.completedFuture(List.of());
            }
            return flowable.toList()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .thenApply(list -> list != null ? list : List.of());
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    private static LlmRequest buildLlmRequest(String prompt) {
        String safePrompt = prompt != null ? prompt : "";
        return LlmRequest.builder()
                .contents(List.of(Content.fromParts(Part.fromText(safePrompt))))
                .build();
    }

    private static String generateText(BaseLlm llm, String prompt) {
        return extractTextFromLlmResponses(generateResponses(llm, prompt));
    }

    private static String extractTextFromLlmResponses(List<LlmResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "";
        }
        List<LlmResponse> nonPartial = responses.stream()
                .filter(Objects::nonNull)
                .filter(r -> !r.partial().orElse(false))
                .toList();
        List<LlmResponse> candidates = nonPartial.isEmpty()
                ? responses.stream().filter(Objects::nonNull).toList()
                : nonPartial;

        StringBuilder sb = new StringBuilder();
        for (LlmResponse response : candidates) {
            if (response.content().isPresent()) {
                String text = extractNonThoughtText(response.content().get());
                if (!text.isEmpty()) {
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    private static CallMetrics toCallMetricsFromLlmResponses(
            List<LlmResponse> responses, long latencyMs, String modelId, PriceTable prices) {
        Integer tokensIn = null;
        Integer tokensOut = null;
        if (responses != null && !responses.isEmpty()) {
            int pSum = 0;
            int cSum = 0;
            for (LlmResponse response : responses) {
                if (response != null
                        && !response.partial().orElse(false)
                        && response.usageMetadata().isPresent()) {
                    GenerateContentResponseUsageMetadata u =
                            response.usageMetadata().get();
                    pSum += u.promptTokenCount().orElse(0);
                    cSum += u.candidatesTokenCount().orElse(0);
                }
            }
            if (pSum > 0 || cSum > 0) {
                tokensIn = pSum;
                tokensOut = cSum;
            }
        }
        Double costUsd = (prices != null && modelId != null && tokensIn != null && tokensOut != null)
                ? prices.costUsd(modelId, tokensIn, tokensOut)
                : null;
        return new CallMetrics(tokensIn, tokensOut, costUsd, latencyMs);
    }

    private static List<Event> runEvents(Runner runner, String input) {
        return runEventsInSession(runner, DEFAULT_USER_ID, UUID.randomUUID().toString(), input);
    }

    private static List<Event> runEventsInSession(Runner runner, String userId, String sessionId, String input) {
        String safeInput = input != null ? input : "";
        Content message = Content.fromParts(Part.fromText(safeInput));
        RunConfig runConfig = RunConfig.builder().autoCreateSession(true).build();
        Flowable<Event> flowable = runner.runAsync(userId, sessionId, message, runConfig);
        if (flowable == null) {
            return List.of();
        }
        List<Event> events = flowable.toList().blockingGet();
        return events != null ? events : List.of();
    }

    private static CompletableFuture<List<Event>> runEventsAsync(Runner runner, String input) {
        try {
            String safeInput = input != null ? input : "";
            Content message = Content.fromParts(Part.fromText(safeInput));
            RunConfig runConfig = RunConfig.builder().autoCreateSession(true).build();
            Flowable<Event> flowable =
                    runner.runAsync(DEFAULT_USER_ID, UUID.randomUUID().toString(), message, runConfig);
            if (flowable == null) {
                return CompletableFuture.completedFuture(List.of());
            }
            return flowable.toList()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .thenApply(list -> list != null ? list : List.of());
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    private static List<Event> nonPartialEvents(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<Event> nonPartial = events.stream()
                .filter(Objects::nonNull)
                .filter(e -> !e.partial().orElse(false))
                .toList();
        return nonPartial.isEmpty() ? events.stream().filter(Objects::nonNull).toList() : nonPartial;
    }

    private static List<ToolCall> matchCallsToResponses(
            List<FunctionCall> calls, List<FunctionResponse> turnResponses) {
        List<ToolCall> matched = new ArrayList<>();
        boolean[] usedResponse = new boolean[turnResponses.size()];

        for (FunctionCall call : calls) {
            if (call == null) {
                continue;
            }
            String name = call.name().orElse("");
            if (name.isBlank()) {
                continue;
            }
            String callId = call.id().orElse("");

            FunctionResponse matchedResponse = null;
            // 1. Match by non-empty call ID first
            if (!callId.isBlank()) {
                for (int r = 0; r < turnResponses.size(); r++) {
                    if (usedResponse[r]) {
                        continue;
                    }
                    FunctionResponse candidate = turnResponses.get(r);
                    if (candidate != null && callId.equals(candidate.id().orElse(""))) {
                        matchedResponse = candidate;
                        usedResponse[r] = true;
                        break;
                    }
                }
            }
            // 2. Fallback: match by tool name in order among unused responses in this turn window
            if (matchedResponse == null) {
                for (int r = 0; r < turnResponses.size(); r++) {
                    if (usedResponse[r]) {
                        continue;
                    }
                    FunctionResponse candidate = turnResponses.get(r);
                    if (candidate != null && name.equals(candidate.name().orElse(""))) {
                        matchedResponse = candidate;
                        usedResponse[r] = true;
                        break;
                    }
                }
            }

            matched.add(toToolCall(call, matchedResponse));
        }

        return matched;
    }

    private static String serializeFunctionResponse(FunctionResponse response) {
        if (response == null || response.response().isEmpty()) {
            return null;
        }
        Map<String, Object> responseMap = response.response().get();
        if (responseMap == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(responseMap);
        } catch (Exception e) {
            return responseMap.toString();
        }
    }

    private static List<String> extractThoughtTexts(Event event) {
        if (event == null
                || event.content().isEmpty()
                || event.content().get().parts().isEmpty()) {
            return List.of();
        }
        List<String> thoughts = new ArrayList<>();
        for (Part part : event.content().get().parts().get()) {
            if (part != null && part.thought().orElse(false) && part.text().isPresent()) {
                String text = part.text().get();
                if (!text.isBlank()) {
                    thoughts.add(text);
                }
            }
        }
        return thoughts;
    }

    private static String extractNonThoughtText(Event event) {
        if (event == null || event.content().isEmpty()) {
            return "";
        }
        return extractNonThoughtText(event.content().get());
    }

    private static String extractNonThoughtText(Content content) {
        if (content == null || content.parts().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Part part : content.parts().get()) {
            if (part != null && !part.thought().orElse(false) && part.text().isPresent()) {
                sb.append(part.text().get());
            }
        }
        return sb.toString();
    }

    private static String extractFinalResponse(List<Event> cleanEvents) {
        for (int i = cleanEvents.size() - 1; i >= 0; i--) {
            Event event = cleanEvents.get(i);
            if (isUserTextEvent(event)) {
                continue;
            }
            if (event.functionResponses() != null && !event.functionResponses().isEmpty()) {
                continue;
            }
            String text = extractNonThoughtText(event);
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private static boolean isUserTextEvent(Event event) {
        if (event == null) {
            return false;
        }
        if (event.functionCalls() != null && !event.functionCalls().isEmpty()) {
            return false;
        }
        if (event.functionResponses() != null && !event.functionResponses().isEmpty()) {
            return false;
        }
        String author = event.author();
        if (author != null && !author.isBlank()) {
            return "user".equalsIgnoreCase(author);
        }
        if (event.content().isPresent() && event.content().get().role().isPresent()) {
            return "user".equalsIgnoreCase(event.content().get().role().get());
        }
        return false;
    }

    private static void appendAssistantTurnMessage(ConversationTrajectory.Builder builder, List<Event> turnEvents) {
        List<ToolCall> turnToolCalls = toToolCalls(turnEvents);
        String turnText = extractFinalResponse(turnEvents);
        if (!turnText.isEmpty() || !turnToolCalls.isEmpty()) {
            builder.assistantMessage(turnText, turnToolCalls);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractSchemaFromDeclaration(FunctionDeclaration decl) {
        if (decl.parameters().isPresent()) {
            Map<String, Object> schema = toSchemaMap(decl.parameters().get());
            if (!schema.isEmpty()) {
                return schema;
            }
        }
        if (decl.parametersJsonSchema().isPresent()) {
            Object raw = decl.parametersJsonSchema().get();
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> copy = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null) {
                        copy.put(k.toString(), v);
                    }
                });
                return Map.copyOf(copy);
            }
            try {
                Map<String, Object> converted = OBJECT_MAPPER.convertValue(raw, new TypeReference<>() {});
                if (converted != null && !converted.isEmpty()) {
                    return Map.copyOf(converted);
                }
            } catch (Exception ignored) {
                // Fall through to default empty object schema
            }
        }
        return Map.of("type", "object", "properties", Map.of());
    }
}
