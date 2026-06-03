package dev.dokimos.springai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.AsyncTask;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.TaskResult;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import reactor.core.publisher.Mono;

/**
 * Utilities for integrating with Spring AI.
 *
 * <p>
 * This class provides bridge methods to use Spring AI components with the
 * Dokimos
 * evaluation framework.
 *
 * <h2>Using Spring AI ChatClient as a Judge</h2>
 *
 * <pre>{@code
 * ChatClient.Builder clientBuilder = ChatClient.builder(chatModel);
 * JudgeLM judge = SpringAiSupport.asJudge(clientBuilder);
 *
 * var evaluator = FaithfulnessEvaluator.builder()
 *         .judge(judge)
 *         .build();
 * }</pre>
 *
 * <h2>Converting Spring AI Evaluation Objects</h2>
 *
 * <pre>{@code
 * // Convert Spring AI EvaluationRequest to Dokimos EvalTestCase
 * EvaluationRequest request = ...;
 * EvalTestCase testCase = SpringAiSupport.toTestCase(request);
 *
 * // Run Dokimos evaluation
 * EvalResult result = evaluator.evaluate(testCase);
 *
 * // Convert back to Spring AI EvaluationResponse
 * EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);
 * }</pre>
 */
public final class SpringAiSupport {

    /**
     * Default key for the model output in evaluation results.
     */
    public static final String OUTPUT_KEY = "output";

    /**
     * Default key for additional context in evaluation results.
     */
    public static final String CONTEXT_KEY = "context";

    /**
     * Default key for reading input from dataset examples.
     */
    public static final String INPUT_KEY = "input";

    private SpringAiSupport() {}

    /**
     * Creates a {@link JudgeLM} from a Spring AI {@link ChatClient.Builder}.
     *
     * <p>
     * Use this to create judges for LLM-based evaluators like
     * {@code LLMJudgeEvaluator}, {@code FaithfulnessEvaluator}, etc.
     *
     * <p>
     * Example:
     *
     * <pre>{@code
     * ChatClient.Builder clientBuilder = ChatClient.builder(chatModel);
     * JudgeLM judge = SpringAiSupport.asJudge(clientBuilder);
     *
     * var evaluator = LLMJudgeEvaluator.builder()
     *         .judge(judge)
     *         .criteria("Is the response helpful?")
     *         .build();
     * }</pre>
     *
     * @param builder the ChatClient.Builder to use as judge
     * @return a JudgeLM that delegates to the ChatClient
     */
    public static JudgeLM asJudge(ChatClient.Builder builder) {
        ChatClient client = builder.build();
        return prompt -> {
            if (prompt == null) {
                throw new IllegalArgumentException("Prompt cannot be null");
            }
            String content = client.prompt().user(prompt).call().content();

            if (content == null) {
                throw new IllegalStateException("Judge response content was null");
            }
            return content;
        };
    }

    /**
     * Creates a {@link JudgeLM} from a Spring AI {@link ChatModel}.
     *
     * <p>
     * This is a convenience overload that accepts a ChatModel directly
     * instead of a {@link ChatClient.Builder}.
     *
     * <p>
     * Example:
     *
     * <pre>{@code
     * ChatModel chatModel = OpenAiChatModel.builder()...build();
     * JudgeLM judge = SpringAiSupport.asJudge(chatModel);
     *
     * var evaluator = FaithfulnessEvaluator.builder()
     *     .judge(judge)
     *     .build();
     * }</pre>
     *
     * @param model the ChatModel to use as judge
     * @return a JudgeLM that delegates to the ChatModel
     */
    public static JudgeLM asJudge(ChatModel model) {
        if (model == null) {
            throw new IllegalArgumentException("ChatModel cannot be null");
        }
        return asJudge(ChatClient.builder(model));
    }

    /**
     * Converts a Spring AI {@link EvaluationRequest} to a Dokimos
     * {@link EvalTestCase}.
     *
     * <p>
     * Maps the following fields:
     * <ul>
     * <li>{@code getUserText()} → input</li>
     * <li>{@code getResponseContent()} → actual output</li>
     * <li>{@code getDataList()} → context (list of document contents)</li>
     * </ul>
     *
     * <p>
     * Example:
     *
     * <pre>{@code
     * EvaluationRequest request = new EvaluationRequest(
     *         userText,
     *         retrievedDocuments,
     *         responseContent);
     *
     * EvalTestCase testCase = SpringAiSupport.toTestCase(request);
     * EvalResult result = faithfulnessEvaluator.evaluate(testCase);
     * }</pre>
     *
     * @param request the Spring AI evaluation request
     * @return an EvalTestCase containing the request data
     */
    public static EvalTestCase toTestCase(EvaluationRequest request) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(INPUT_KEY, request.getUserText());

        Map<String, Object> actualOutputs = new HashMap<>();
        actualOutputs.put(OUTPUT_KEY, request.getResponseContent());

        // Extract context from retrieved documents
        List<Document> documents = request.getDataList();
        if (documents != null && !documents.isEmpty()) {
            List<String> contextTexts =
                    documents.stream().map(Document::getText).toList();
            actualOutputs.put(CONTEXT_KEY, contextTexts);
        }

        return new EvalTestCase(inputs, actualOutputs, Map.of(), Map.of());
    }

    /**
     * Converts a Dokimos {@link EvalResult} to a Spring AI
     * {@link EvaluationResponse}.
     *
     * <p>
     * Maps the following fields:
     * <ul>
     * <li>{@code score} -> metadata["score"] (as float)</li>
     * <li>{@code success} -> pass/fail status</li>
     * <li>{@code reason} -> the reasoning text</li>
     * <li>{@code metadata} -> preserved in response metadata</li>
     * </ul>
     *
     * <p>
     * Example:
     *
     * <pre>{@code
     * EvalResult result = evaluator.evaluate(testCase);
     * EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);
     *
     * System.out.println("Score: " + response.getMetadata().get("score"));
     * System.out.println("Passed: " + response.isPass());
     * System.out.println("Feedback: " + response.getFeedback());
     * }</pre>
     *
     * @param result the Dokimos evaluation result
     * @return an EvaluationResponse containing the result data
     */
    public static EvaluationResponse toEvaluationResponse(EvalResult result) {
        Map<String, Object> metadata = new HashMap<>(result.metadata());
        metadata.put("score", (float) result.score());

        return new EvaluationResponse(result.success(), (float) result.score(), result.reason(), metadata);
    }

    /**
     * Creates an {@link AsyncTask} that calls a Spring AI {@link ChatClient} off the calling
     * thread and writes the response under the {@link #OUTPUT_KEY default output key}.
     *
     * <p>The example's {@link dev.dokimos.core.Example#input() input} is sent as the user message.
     * The {@link ChatClient#prompt()} call is dispatched on the common {@link java.util.concurrent.ForkJoinPool}
     * via {@link CompletableFuture#supplyAsync(java.util.function.Supplier)} so the experiment's
     * async execution path can keep many calls in flight without a blocked thread per example.
     *
     * <p>Example:
     * <pre>{@code
     * ChatClient client = ChatClient.builder(chatModel).build();
     * AsyncTask task = SpringAiSupport.asyncTask(client);
     *
     * Experiment.builder()
     *     .asyncTask(task)
     *     .parallelism(8)
     *     .evaluators(List.of(evaluator))
     *     .build()
     *     .run();
     * }</pre>
     *
     * @param client the ChatClient to call, never null
     * @return an AsyncTask suitable for {@code Experiment.builder().asyncTask(...)}
     * @throws IllegalArgumentException if {@code client} is null
     */
    public static AsyncTask asyncTask(ChatClient client) {
        return asyncTask(client, INPUT_KEY, OUTPUT_KEY);
    }

    /**
     * Creates an {@link AsyncTask} that calls a Spring AI {@link ChatClient} off the calling thread
     * using caller-chosen input and output keys.
     *
     * <p>Behaves like {@link #asyncTask(ChatClient)} but reads the user message from {@code inputKey}
     * and writes the response under {@code outputKey}, for datasets or evaluators that use different
     * key names.
     *
     * @param client    the ChatClient to call, never null
     * @param inputKey  the key to read the user message from the example inputs, never null
     * @param outputKey the key the response is written under in the result, never null
     * @return an AsyncTask suitable for {@code Experiment.builder().asyncTask(...)}
     * @throws IllegalArgumentException if any argument is null
     */
    public static AsyncTask asyncTask(ChatClient client, String inputKey, String outputKey) {
        if (client == null) {
            throw new IllegalArgumentException("ChatClient cannot be null");
        }
        if (inputKey == null) {
            throw new IllegalArgumentException("inputKey cannot be null");
        }
        if (outputKey == null) {
            throw new IllegalArgumentException("outputKey cannot be null");
        }
        return example -> CompletableFuture.supplyAsync(() -> {
            Object input = example.inputs().get(inputKey);
            String content =
                    client.prompt().user(String.valueOf(input)).call().content();
            return TaskResult.of(Map.of(outputKey, content != null ? content : ""));
        });
    }

    /**
     * Adapts a Reactor {@link Mono} of {@link TaskResult} to an {@link AsyncTask}.
     *
     * <p>This is the bridge for reactive Spring AI pipelines: supply a function that produces a
     * {@code Mono<TaskResult>} for an example, and the resulting task converts each Mono to a
     * {@link CompletableFuture} via {@link Mono#toFuture()}. Reactor lives on the Spring AI classpath
     * (provided scope), so dokimos-core stays free of any Reactor dependency.
     *
     * <p>Example:
     * <pre>{@code
     * AsyncTask task = SpringAiSupport.reactiveTask(example ->
     *     reactiveChatClient.prompt()
     *         .user(example.input())
     *         .stream()
     *         .content()
     *         .collectList()
     *         .map(parts -> TaskResult.of(Map.of("output", String.join("", parts)))));
     * }</pre>
     *
     * @param taskFunction a function producing a {@code Mono<TaskResult>} for an example, never null
     * @return an AsyncTask backed by the supplied Mono
     * @throws IllegalArgumentException if {@code taskFunction} is null
     */
    public static AsyncTask reactiveTask(Function<dev.dokimos.core.Example, Mono<TaskResult>> taskFunction) {
        if (taskFunction == null) {
            throw new IllegalArgumentException("taskFunction cannot be null");
        }
        return example -> taskFunction.apply(example).toFuture();
    }

    /**
     * Adapts a Reactor {@link Mono} of {@code String} output to an {@link AsyncTask}, wrapping the
     * emitted string under the {@link #OUTPUT_KEY default output key}.
     *
     * <p>Convenience over {@link #reactiveTask(Function)} for the common case where the reactive
     * pipeline yields the model's textual response directly. A {@code null} emission is stored as an
     * empty string.
     *
     * <p>Example:
     * <pre>{@code
     * AsyncTask task = SpringAiSupport.reactiveStringTask(example ->
     *     reactiveChatClient.prompt().user(example.input()).stream().content().last());
     * }</pre>
     *
     * @param taskFunction a function producing a {@code Mono<String>} response for an example, never null
     * @return an AsyncTask that writes the emitted string under the default output key
     * @throws IllegalArgumentException if {@code taskFunction} is null
     */
    public static AsyncTask reactiveStringTask(Function<dev.dokimos.core.Example, Mono<String>> taskFunction) {
        if (taskFunction == null) {
            throw new IllegalArgumentException("taskFunction cannot be null");
        }
        return example -> taskFunction
                .apply(example)
                .map(output -> TaskResult.of(Map.of(OUTPUT_KEY, output != null ? output : "")))
                .defaultIfEmpty(TaskResult.of(Map.of(OUTPUT_KEY, "")))
                .toFuture();
    }

    private static final ObjectMapper TOOL_ARG_MAPPER = new ObjectMapper();

    /**
     * Builds an {@link AgentTrace} from a Spring AI {@link AssistantMessage}.
     *
     * <p>The message's text becomes the final response and its
     * {@code getToolCalls()} become {@link ToolCall}s with parsed arguments. Tool
     * results are not part of an {@code AssistantMessage}; use
     * {@link #toAgentTrace(AssistantMessage, List)} to attach them.
     *
     * @param message the assistant message (may be null)
     * @return an agent trace, never null
     */
    public static AgentTrace toAgentTrace(AssistantMessage message) {
        return toAgentTrace(message, List.of());
    }

    /**
     * Builds an {@link AgentTrace} from a Spring AI {@link AssistantMessage} and the
     * tool responses produced for it.
     *
     * <p>Each tool call is matched to its result by tool-call id from the supplied
     * {@link ToolResponseMessage}s, so the resulting trace carries both the agent's
     * tool calls and what those tools returned.
     *
     * <pre>{@code
     * AgentTrace trace = SpringAiSupport.toAgentTrace(assistantMessage, toolResponseMessages);
     * EvalTestCase testCase = trace.toTestCase(userMessage, tools);
     * }</pre>
     *
     * @param message       the assistant message (may be null)
     * @param toolResponses the tool response messages whose responses carry results (may be null)
     * @return an agent trace, never null
     */
    public static AgentTrace toAgentTrace(AssistantMessage message, List<ToolResponseMessage> toolResponses) {
        AgentTrace.Builder builder = AgentTrace.builder().toolCalls(toToolCalls(message, toolResponses));
        if (message != null && message.getText() != null) {
            builder.finalResponse(message.getText());
        }
        return builder.build();
    }

    /**
     * Extracts {@link ToolCall}s from a Spring AI {@link AssistantMessage} without results.
     *
     * @param message the assistant message (may be null)
     * @return the tool calls in order, or an empty list
     */
    public static List<ToolCall> toToolCalls(AssistantMessage message) {
        return toToolCalls(message, List.of());
    }

    /**
     * Extracts {@link ToolCall}s from a Spring AI {@link AssistantMessage}, attaching
     * results from the supplied tool responses by tool-call id.
     *
     * @param message       the assistant message (may be null)
     * @param toolResponses the tool response messages (may be null)
     * @return the tool calls in order, or an empty list
     */
    public static List<ToolCall> toToolCalls(AssistantMessage message, List<ToolResponseMessage> toolResponses) {
        if (message == null || message.getToolCalls() == null) {
            return List.of();
        }
        Map<String, String> resultsById = resultsById(toolResponses);
        return message.getToolCalls().stream()
                .map(call -> ToolCall.builder()
                        .name(call.name())
                        .arguments(parseArguments(call.arguments()))
                        .result(resultsById.get(call.id()))
                        .build())
                .toList();
    }

    /**
     * Converts Spring AI {@link org.springframework.ai.tool.definition.ToolDefinition}s
     * to Dokimos {@link ToolDefinition}s so tool calls can be evaluated against the
     * tools the agent was given.
     *
     * @param toolDefinitions the Spring AI tool definitions (may be null)
     * @return the Dokimos tool definitions, or an empty list
     */
    public static List<ToolDefinition> toToolDefinitions(
            List<org.springframework.ai.tool.definition.ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null) {
            return List.of();
        }
        return toolDefinitions.stream()
                .map(def -> ToolDefinition.builder()
                        .name(def.name())
                        .description(def.description() != null ? def.description() : "")
                        .inputSchema(parseSchema(def.inputSchema()))
                        .build())
                .toList();
    }

    private static Map<String, String> resultsById(List<ToolResponseMessage> toolResponses) {
        if (toolResponses == null) {
            return Map.of();
        }
        Map<String, String> byId = new LinkedHashMap<>();
        for (ToolResponseMessage message : toolResponses) {
            if (message.getResponses() == null) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse response : message.getResponses()) {
                byId.put(response.id(), response.responseData());
            }
        }
        return byId;
    }

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

    private static Map<String, Object> parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = TOOL_ARG_MAPPER.readValue(schemaJson, new TypeReference<>() {});
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }
}
