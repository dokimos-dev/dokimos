package dev.dokimos.langchain4j;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.Task;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilities for integrating with LangChain4j.
 *
 * <p>This class provides factory methods to create {@link Task}s and {@link JudgeLM}s
 * from LangChain4j components.
 *
 * <h2>RAG Evaluation</h2>
 * <pre>{@code
 * // 1. Define your AiService to return Result<String>
 * interface Assistant {
 *     Result<String> chat(String userMessage);
 * }
 *
 * // 2. Build your assistant
 * Assistant assistant = AiServices.builder(Assistant.class)
 *     .chatModel(chatModel)
 *     .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
 *         .queryTransformer(compressingQueryTransformer)
 *         .contentRetriever(retriever)
 *         .contentAggregator(reRankingAggregator)
 *         .build())
 *     .build();
 *
 * // 3. Create a Task for evaluation
 * Task task = LangChain4jSupport.ragTask(assistant::chat);
 *
 * // 4. Run evaluation with some metrics
 * Experiment.builder()
 *     .task(task)
 *     .evaluators(List.of(faithfulness, contextRelevancy))
 *     .build()
 *     .run();
 * }</pre>
 */
public final class LangChain4jSupport {

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

    private LangChain4jSupport() {}

    /**
     * Creates a {@link JudgeLM} from a LangChain4j {@link ChatModel}.
     *
     * <p>Use this to create judges for LLM-based evaluators like
     * {@code LLMJudgeEvaluator}, {@code FaithfulnessEvaluator}, etc.
     *
     * <p>Example:
     * <pre>{@code
     * ChatModel gemini = VertexAiGeminiChatModel.builder()...build();
     * JudgeLM judge = LangChain4jSupport.asJudge(gemini);
     *
     * var evaluator = LLMJudgeEvaluator.builder()
     *     .judge(judge)
     *     .criteria("Is the response helpful?")
     *     .build();
     * }</pre>
     *
     * @param model the ChatModel to use as judge
     * @return a JudgeLM that delegates to the ChatModel
     */
    public static JudgeLM asJudge(ChatModel model) {
        return prompt -> {
            String content = model.chat(prompt);
            if (content == null) {
                throw new IllegalStateException("Judge response content was null");
            }
            return content;
        };
    }

    /**
     * Creates a simple {@link Task} for Q&amp;A evaluation.
     *
     * <p>The task reads "input" from the example and returns a Map with "output".
     *
     * <p>Example:
     * <pre>{@code
     * ChatModel model = OpenAiChatModel.builder()...build();
     * Task task = LangChain4jSupport.simpleTask(model);
     *
     * // Dataset examples just need "input"
     * Example example = Example.of("What is 2+2?", "4");
     * }</pre>
     *
     * @param model the ChatModel to evaluate
     * @return a Task suitable for the Experiment
     */
    public static Task simpleTask(ChatModel model) {
        return simpleTask(model, OUTPUT_KEY);
    }

    /**
     * Creates a simple {@link Task} for Q&amp;A evaluation that writes the response
     * under a caller-chosen key.
     *
     * <p>Behaves like {@link #simpleTask(ChatModel)} but lets you override the
     * {@link #OUTPUT_KEY default output key} when your evaluators or dataset expect
     * a different name.
     *
     * <p>Example:
     * <pre>{@code
     * ChatModel model = OpenAiChatModel.builder()...build();
     * Task task = LangChain4jSupport.simpleTask(model, "answer");
     * }</pre>
     *
     * @param model     the ChatModel to evaluate
     * @param outputKey the key for the output in the result map
     * @return a Task suitable for the Experiment
     */
    public static Task simpleTask(ChatModel model, String outputKey) {
        return example -> {
            String output = model.chat(example.input());
            return Map.of(outputKey, output != null ? output : "");
        };
    }

    /**
     * Creates a RAG evaluation {@link Task} from a function that returns {@link Result}.
     *
     * <p>This is the primary integration point for RAG evaluation. LangChain4j's
     * Result class already contains the retrieved sources via {@code result.sources()}.
     *
     * <p>Example:
     * <pre>{@code
     * interface Assistant {
     *     Result<String> chat(String userMessage);
     * }
     *
     * Assistant assistant = AiServices.builder(Assistant.class)
     *     .chatModel(chatModel)
     *     .retrievalAugmentor(retrievalAugmentor)
     *     .build();
     *
     * Task task = LangChain4jSupport.ragTask(assistant::chat);
     * }</pre>
     *
     * @param assistantCall a function that takes the input string and returns a Result
     * @return a Task suitable for evaluation
     */
    public static Task ragTask(Function<String, Result<String>> assistantCall) {
        return ragTask(assistantCall, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY);
    }

    /**
     * Creates a RAG evaluation {@link Task} with custom key names.
     *
     * <p>Use this when your dataset or evaluators expect different keys.
     *
     * <p>Example:
     * <pre>{@code
     * // Dataset uses "question" instead of "input"
     * Task task = LangChain4jSupport.ragTask(
     *     assistant::chat,
     *     "question",        // input key
     *     "answer",          // output key
     *     "retrievalContext" // context key
     * );
     * }</pre>
     *
     * @param assistantCall a function that takes the input string and returns a Result
     * @param inputKey      the key to read from example inputs
     * @param outputKey     the key for the output in the result map
     * @param contextKey    the key for the retrieval context in the result map
     * @return a Task suitable for RAG evaluation
     */
    public static Task ragTask(
            Function<String, Result<String>> assistantCall, String inputKey, String outputKey, String contextKey) {
        return example -> {
            String input = (String) example.inputs().get(inputKey);
            Result<String> result = assistantCall.apply(input);

            Map<String, Object> outputs = new HashMap<>();
            outputs.put(outputKey, result.content());
            outputs.put(contextKey, extractTexts(result.sources()));
            return outputs;
        };
    }

    /**
     * Creates a flexible {@link Task} that allows full control over output mapping.
     *
     * <p>Use this for complex scenarios where you want to capture additional data
     * beyond what the standard RAG task implementation provides.
     *
     * <p>Example:
     * <pre>{@code
     * Task task = LangChain4jSupport.customTask(example -> {
     *     String query = example.input();
     *
     *     // Track the latency
     *     long start = System.currentTimeMillis();
     *     Result<String> result = assistant.chat(query);
     *     long duration = System.currentTimeMillis() - start;
     *
     *     return Map.of(
     *         "output", result.content(),
     *         "context", LangChain4jSupport.extractTexts(result.sources()),
     *         "latencyMs", duration,
     *         "sourceCount", result.sources().size()
     *     );
     * });
     * }</pre>
     *
     * @param taskFunction a function that takes an Example and returns outputs
     * @return a Task suitable for Experiment
     */
    public static Task customTask(Task taskFunction) {
        return taskFunction;
    }

    /**
     * Extracts text content from a list of LangChain4j {@link Content} objects.
     *
     * <p>This is useful when building custom Tasks.
     *
     * @param contents the list of Content from result.sources()
     * @return list of text strings, empty list if contents is null
     */
    public static List<String> extractTexts(List<Content> contents) {
        if (contents == null) {
            return List.of();
        }
        return contents.stream()
                .filter(c -> c.textSegment() != null)
                .map(c -> c.textSegment().text())
                .toList();
    }

    /**
     * Extracts text content with metadata from a list of LangChain4j {@link Content} objects.
     *
     * <p>Returns a list of maps, where each map contains:
     * <ul>
     *   <li>{@code text} - the segment text</li>
     *   <li>{@code metadata} - the segment metadata as a map</li>
     * </ul>
     *
     * <p>This is useful when you need source attribution in evaluations.
     *
     * @param contents the list of Content from result.sources()
     * @return list of maps containing text and metadata
     */
    public static List<Map<String, Object>> extractTextsWithMetadata(List<Content> contents) {
        if (contents == null) {
            return List.of();
        }
        return contents.stream()
                .filter(c -> c.textSegment() != null)
                .map(c -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("text", c.textSegment().text());
                    entry.put("metadata", c.textSegment().metadata().toMap());
                    return entry;
                })
                .toList();
    }

    private static final ObjectMapper TOOL_ARG_MAPPER = new ObjectMapper();

    /**
     * Builds an {@link AgentTrace} from a LangChain4j {@link Result}.
     *
     * <p>The result's {@code content()} becomes the final response and its
     * {@code toolExecutions()} become {@link ToolCall}s carrying the tool name, parsed
     * arguments, and the tool result string. Use it to evaluate tool-calling agents
     * built with {@code AiServices} that return {@code Result<T>}.
     *
     * <pre>{@code
     * Result<String> result = assistant.chat(userMessage);
     * AgentTrace trace = LangChain4jSupport.toAgentTrace(result);
     * EvalTestCase testCase = trace.toTestCase(userMessage, tools);
     * }</pre>
     *
     * @param result the LangChain4j result (may be null)
     * @return an agent trace, never null
     */
    public static AgentTrace toAgentTrace(Result<?> result) {
        AgentTrace.Builder builder = AgentTrace.builder().toolCalls(toToolCalls(result));
        if (result != null && result.content() != null) {
            builder.finalResponse(String.valueOf(result.content()));
        }
        return builder.build();
    }

    /**
     * Extracts {@link ToolCall}s from a LangChain4j {@link Result} in execution order.
     *
     * @param result the result (may be null)
     * @return the tool calls, or an empty list when there are none
     */
    public static List<ToolCall> toToolCalls(Result<?> result) {
        if (result == null || result.toolExecutions() == null) {
            return List.of();
        }
        return result.toolExecutions().stream()
                .map(LangChain4jSupport::toToolCall)
                .toList();
    }

    /**
     * Converts a single LangChain4j {@link ToolExecution} to a {@link ToolCall}.
     *
     * @param execution the tool execution
     * @return the tool call
     */
    public static ToolCall toToolCall(ToolExecution execution) {
        ToolExecutionRequest request = execution.request();
        return ToolCall.builder()
                .name(request.name())
                .arguments(parseArguments(request.arguments()))
                .result(execution.result())
                .build();
    }

    /**
     * Converts LangChain4j {@link ToolSpecification}s to {@link ToolDefinition}s so tool
     * calls can be evaluated against the tools the agent was given.
     *
     * @param specifications the tool specifications (may be null)
     * @return the tool definitions, or an empty list
     */
    public static List<ToolDefinition> toToolDefinitions(List<ToolSpecification> specifications) {
        if (specifications == null) {
            return List.of();
        }
        return specifications.stream().map(LangChain4jSupport::toToolDefinition).toList();
    }

    /**
     * Converts a single {@link ToolSpecification} to a {@link ToolDefinition}.
     *
     * @param specification the tool specification
     * @return the tool definition
     */
    public static ToolDefinition toToolDefinition(ToolSpecification specification) {
        return ToolDefinition.builder()
                .name(specification.name())
                .description(specification.description() != null ? specification.description() : "")
                .inputSchema(toSchemaMap(specification.parameters()))
                .build();
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

    private static Map<String, Object> toSchemaMap(JsonObjectSchema schema) {
        if (schema == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        if (schema.properties() != null) {
            schema.properties().forEach((name, element) -> properties.put(name, elementToMap(element)));
        }
        map.put("properties", properties);
        if (schema.required() != null && !schema.required().isEmpty()) {
            map.put("required", List.copyOf(schema.required()));
        }
        return map;
    }

    private static Map<String, Object> elementToMap(JsonSchemaElement element) {
        Map<String, Object> map = new LinkedHashMap<>();
        String type = jsonType(element);
        if (type != null) {
            map.put("type", type);
        }
        if (element instanceof JsonArraySchema array && array.items() != null) {
            map.put("items", elementToMap(array.items()));
        } else if (element instanceof JsonObjectSchema object) {
            Map<String, Object> properties = new LinkedHashMap<>();
            if (object.properties() != null) {
                object.properties().forEach((name, child) -> properties.put(name, elementToMap(child)));
            }
            map.put("properties", properties);
            if (object.required() != null && !object.required().isEmpty()) {
                map.put("required", List.copyOf(object.required()));
            }
        }
        return map;
    }

    private static String jsonType(JsonSchemaElement element) {
        if (element instanceof JsonStringSchema) return "string";
        if (element instanceof JsonIntegerSchema) return "integer";
        if (element instanceof JsonNumberSchema) return "number";
        if (element instanceof JsonBooleanSchema) return "boolean";
        if (element instanceof JsonArraySchema) return "array";
        if (element instanceof JsonObjectSchema) return "object";
        return null;
    }
}
