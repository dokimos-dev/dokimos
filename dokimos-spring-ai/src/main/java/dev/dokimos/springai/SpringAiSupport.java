package dev.dokimos.springai;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private SpringAiSupport() {
    }

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
            String content = client.prompt()
                    .user(prompt)
                    .call()
                    .content();

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
            List<String> contextTexts = documents.stream()
                    .map(Document::getText)
                    .toList();
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

        return new EvaluationResponse(
                result.success(),
                result.reason(),
                metadata);
    }
}
