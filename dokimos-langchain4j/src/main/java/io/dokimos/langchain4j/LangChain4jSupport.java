package io.dokimos.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import io.dokimos.core.JudgeLM;
import io.dokimos.core.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilities for integrating with LangChain4j.
 *
 * <p>This class provides factory methods to create {@link Task}s and {@link JudgeLM}s
 * from LangChain4j components.
 * <p>
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

    public static final String OUTPUT_KEY = "output";
    public static final String CONTEXT_KEY = "context";
    public static final String INPUT_KEY = "input";

    private LangChain4jSupport() {
    }

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
        return model::chat;
    }

    /**
     * Creates a simple {@link Task} for Q&A evaluation.
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
        return example -> Map.of(OUTPUT_KEY, model.chat(example.input()));
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
            Function<String, Result<String>> assistantCall,
            String inputKey,
            String outputKey,
            String contextKey
    ) {
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
    
}