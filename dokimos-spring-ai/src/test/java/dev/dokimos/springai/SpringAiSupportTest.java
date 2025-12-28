package dev.dokimos.springai;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the {@link SpringAiSupport} utility class.
 */
class SpringAiSupportTest {

        @Test
        void asJudge_withChatClientBuilder_shouldDelegateToChatClient() {
                ChatModel mockModel = prompt -> new ChatResponse(
                                List.of(new Generation(new AssistantMessage("Judge response"))));

                ChatClient.Builder builder = ChatClient.builder(mockModel);
                JudgeLM judge = SpringAiSupport.asJudge(builder);

                String response = judge.generate("Test prompt");
                assertThat(response).isEqualTo("Judge response");
        }

        @Test
        void asJudge_withChatModel_shouldDelegateToChatModel() {
                ChatModel mockModel = prompt -> new ChatResponse(
                                List.of(new Generation(new AssistantMessage("Model response"))));

                JudgeLM judge = SpringAiSupport.asJudge(mockModel);

                String response = judge.generate("Evaluate this");
                assertThat(response).isEqualTo("Model response");
        }

        @Test
        void asJudge_withChatModel_shouldPassPromptAsUserMessage() {
                final String[] capturedUserText = { null };

                ChatModel mockModel = prompt -> {
                        capturedUserText[0] = prompt.getInstructions().get(0).getContent();
                        return new ChatResponse(
                                        List.of(new Generation(new AssistantMessage("Response"))));
                };

                JudgeLM judge = SpringAiSupport.asJudge(mockModel);
                judge.generate("My evaluation prompt");

                assertThat(capturedUserText[0]).isEqualTo("My evaluation prompt");
        }

        @Test
        void toTestCase_shouldMapUserTextToInput() {
                EvaluationRequest request = new EvaluationRequest(
                                "What is the capital of France?",
                                List.of(),
                                "Paris is the capital of France.");

                EvalTestCase testCase = SpringAiSupport.toTestCase(request);

                assertThat(testCase.input()).isEqualTo("What is the capital of France?");
                assertThat(testCase.inputs()).containsEntry("input", "What is the capital of France?");
        }

        @Test
        void toTestCase_shouldMapResponseContentToActualOutput() {
                EvaluationRequest request = new EvaluationRequest(
                                "Question",
                                List.of(),
                                "The answer is 42.");

                EvalTestCase testCase = SpringAiSupport.toTestCase(request);

                assertThat(testCase.actualOutput()).isEqualTo("The answer is 42.");
                assertThat(testCase.actualOutputs()).containsEntry("output", "The answer is 42.");
        }

        @Test
        void toTestCase_shouldMapDocumentsToContext() {
                List<Document> documents = List.of(
                                new Document("Document 1 content"),
                                new Document("Document 2 content"),
                                new Document("Document 3 content"));

                EvaluationRequest request = new EvaluationRequest(
                                "Question",
                                documents,
                                "Answer based on documents");

                EvalTestCase testCase = SpringAiSupport.toTestCase(request);

                assertThat(testCase.actualOutputs()).containsKey("context");

                @SuppressWarnings("unchecked")
                List<String> context = (List<String>) testCase.actualOutputs().get("context");

                assertThat(context).containsExactly(
                                "Document 1 content",
                                "Document 2 content",
                                "Document 3 content");
        }

        @Test
        void toTestCase_shouldHandleEmptyDocumentList() {
                EvaluationRequest request = new EvaluationRequest(
                                "Question",
                                List.of(),
                                "Answer");

                EvalTestCase testCase = SpringAiSupport.toTestCase(request);

                assertThat(testCase.actualOutputs()).doesNotContainKey("context");
        }

        @Test
        void toTestCase_shouldHandleNullDocumentList() {
                EvaluationRequest request = new EvaluationRequest(
                                "Question",
                                null,
                                "Answer");

                EvalTestCase testCase = SpringAiSupport.toTestCase(request);

                assertThat(testCase.actualOutputs()).doesNotContainKey("context");
        }

        @Test
        void toTestCase_shouldHaveEmptyExpectedOutputsAndMetadata() {
                EvaluationRequest request = new EvaluationRequest(
                                "Question",
                                List.of(),
                                "Answer");

                EvalTestCase testCase = SpringAiSupport.toTestCase(request);

                assertThat(testCase.expectedOutputs()).isEmpty();
                assertThat(testCase.metadata()).isEmpty();
        }

        @Test
        void toEvaluationResponse_shouldMapSuccessToPass() {
                EvalResult successResult = EvalResult.success(
                                "faithfulness",
                                0.95,
                                "Response is faithful to context");

                EvaluationResponse response = SpringAiSupport.toEvaluationResponse(successResult);

                assertThat(response.isPass()).isTrue();
        }

        @Test
        void toEvaluationResponse_shouldMapFailureToNotPass() {
                EvalResult failureResult = EvalResult.failure(
                                "faithfulness",
                                0.3,
                                "Response contains hallucinations");

                EvaluationResponse response = SpringAiSupport.toEvaluationResponse(failureResult);

                assertThat(response.isPass()).isFalse();
        }

        @Test
        void toEvaluationResponse_shouldMapScore() {
                EvalResult result = EvalResult.success(
                                "relevancy",
                                0.87,
                                "Highly relevant");

                EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);

                assertThat(response.getMetadata()).containsEntry("score", 0.87f);
        }

        @Test
        void toEvaluationResponse_shouldMapReason() {
                EvalResult result = EvalResult.success(
                                "coherence",
                                0.92,
                                "The response is well-structured and coherent");

                EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);

                assertThat(response.getFeedback()).isEqualTo("The response is well-structured and coherent");
        }

        @Test
        void toEvaluationResponse_shouldMapMetadata() {
                EvalResult result = new EvalResult(
                                "custom-evaluator",
                                0.75,
                                true,
                                "Evaluation passed",
                                Map.of(
                                                "model", "gpt-5",
                                                "temperature", 0.7,
                                                "tokens", 150));

                EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);

                assertThat(response.getMetadata()).containsEntry("model", "gpt-5");
                assertThat(response.getMetadata()).containsEntry("temperature", 0.7);
                assertThat(response.getMetadata()).containsEntry("tokens", 150);
        }

        @Test
        void toEvaluationResponse_shouldHandleEmptyMetadata() {
                EvalResult result = EvalResult.success(
                                "evaluator",
                                0.8,
                                "Good");

                EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);

                assertThat(response.getMetadata()).hasSize(1);
                assertThat(response.getMetadata()).containsEntry("score", 0.8f);
        }

        @Test
        void roundTripConversion_shouldPreserveData() {
                List<Document> documents = List.of(
                                new Document("Context document 1"),
                                new Document("Context document 2"));

                EvaluationRequest request = new EvaluationRequest(
                                "What is RAG?",
                                documents,
                                "RAG stands for Retrieval-Augmented Generation");

                EvalResult result = new EvalResult(
                                "faithfulness",
                                0.9,
                                true,
                                "Response is faithful",
                                Map.of("confidence", 0.95));

                EvalTestCase testCase = SpringAiSupport.toTestCase(request);
                EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);

                assertThat(testCase.input()).isEqualTo("What is RAG?");
                assertThat(testCase.actualOutput()).isEqualTo("RAG stands for Retrieval-Augmented Generation");

                @SuppressWarnings("unchecked")
                List<String> context = (List<String>) testCase.actualOutputs().get("context");
                assertThat(context).hasSize(2);

                assertThat(response.isPass()).isTrue();
                assertThat(response.getMetadata()).containsEntry("score", 0.9f);
                assertThat(response.getFeedback()).isEqualTo("Response is faithful");
                assertThat(response.getMetadata()).containsEntry("confidence", 0.95);
        }

        @Test
        void constants_shouldHaveExpectedValues() {
                assertThat(SpringAiSupport.OUTPUT_KEY).isEqualTo("output");
                assertThat(SpringAiSupport.CONTEXT_KEY).isEqualTo("context");
                assertThat(SpringAiSupport.INPUT_KEY).isEqualTo("input");
        }
}
