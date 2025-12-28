package dev.dokimos.examples.springai;

import dev.dokimos.core.*;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * An Example demonstrating basic RAG evaluation with Spring AI and Dokimos.
 * Shows how to evaluate faithfulness of responses to retrieved context.
 * Requires the `OPENAI_API_KEY` environment variable.
 */
public class SpringAiRAGExample {

        @SuppressWarnings("null")
        public static void main(String[] args) {
                // Validate the OPENAI_API_KEY env variable
                if (System.getenv("OPENAI_API_KEY") == null) {
                        System.err.println("OPENAI_API_KEY not set");
                        System.exit(1);
                }

                // 1. Set up Spring AI components
                OpenAiApi openAiApi = new OpenAiApi(System.getenv("OPENAI_API_KEY"));

                EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi);
                @SuppressWarnings("removal")
                VectorStore vectorStore = new SimpleVectorStore(embeddingModel);

                // 2. Ingest documents into vector store
                List<Document> documents = List.of(
                                new Document("We offer a 30-day money-back guarantee on all purchases. " +
                                                "No questions asked."),
                                new Document("Standard shipping takes 5-7 business days. " +
                                                "Express shipping is available for 2-3 days."),
                                new Document("All products come with a 1-year manufacturer warranty. " +
                                                "Extended warranties available for purchase."));

                vectorStore.add(documents);

                // 3. Set up the ChatModel for generation
                ChatModel chatModel = new OpenAiChatModel(openAiApi,
                                OpenAiChatOptions.builder()
                                                .model("gpt-5-nano")
                                                .build());

                ChatClient chatClient = ChatClient.builder(chatModel).build();

                // 4. Create dataset
                Dataset dataset = Dataset.builder()
                                .name("customer-qa-rag")
                                .addExample(Example.of(
                                                "What is the refund policy?",
                                                "30-day money-back guarantee"))
                                .addExample(Example.of(
                                                "How long does shipping take?",
                                                "5-7 business days"))
                                .addExample(Example.of(
                                                "What warranty do you offer?",
                                                "1-year warranty"))
                                .build();

                // 5. Create RAG task that retrieves context and generates answer
                Task ragTask = example -> {
                        String query = example.input();

                        // Retrieve relevant documents
                        List<Document> retrievedDocs = vectorStore.similaritySearch(
                                        SearchRequest.builder().query(query).topK(2).build());

                        // Extract context text
                        List<String> context = retrievedDocs.stream()
                                        .map(Document::getText)
                                        .toList();

                        // Create context-aware prompt
                        String contextText = String.join("\n", context);
                        String prompt = String.format(
                                        "Answer the following question based on the provided context.\n\n" +
                                                        "Context:\n%s\n\n" +
                                                        "Question: %s\n\n" +
                                                        "Answer:",
                                        contextText, query);

                        // Generate response
                        String response = chatClient.prompt()
                                        .user(prompt)
                                        .call()
                                        .content();

                        return Map.of(
                                        "output", response,
                                        "context", context);
                };

                // 6. Set up evaluators with judge model
                ChatModel judgeModel = new OpenAiChatModel(openAiApi,
                                OpenAiChatOptions.builder()
                                                .model("gpt-5-nano")
                                                .build());

                JudgeLM judge = SpringAiSupport.asJudge(judgeModel);

                List<Evaluator> evaluators = List.of(
                                LLMJudgeEvaluator.builder()
                                                .name("Answer Quality")
                                                .judge(judge)
                                                .criteria("Is the answer accurate and helpful?")
                                                .evaluationParams(List.of(
                                                                EvalTestCaseParam.INPUT,
                                                                EvalTestCaseParam.ACTUAL_OUTPUT))
                                                .threshold(0.7)
                                                .build(),
                                FaithfulnessEvaluator.builder()
                                                .name("Faithfulness")
                                                .threshold(0.8)
                                                .judge(judge)
                                                .build());

                // 7. Run experiment
                ExperimentResult result = Experiment.builder()
                                .name("Spring AI RAG Evaluation")
                                .dataset(dataset)
                                .task(ragTask)
                                .evaluators(evaluators)
                                .build()
                                .run();

                // 8. Display results
                System.out.println("=".repeat(70));
                System.out.println("Spring AI RAG Evaluation Results");
                System.out.println("=".repeat(70));
                System.out.println("Overall Pass Rate: " + String.format("%.0f%%", result.passRate() * 100));
                System.out.println();

                System.out.println("Average Scores:");
                System.out.println("  Answer Quality:    " +
                                String.format("%.2f", result.averageScore("Answer Quality")));
                System.out.println("  Faithfulness:      " +
                                String.format("%.2f", result.averageScore("Faithfulness")));
                System.out.println();

                System.out.println("Detailed Results:");
                System.out.println("-".repeat(70));
                result.itemResults().forEach(item -> {
                        System.out.println();
                        System.out.println("Question: " + item.example().input());
                        System.out.println("Expected: " + item.example().expectedOutput());
                        System.out.println();
                        System.out.println("Retrieved Context:");
                        @SuppressWarnings("unchecked")
                        List<String> context = (List<String>) item.actualOutputs().get("context");
                        context.forEach(ctx -> System.out
                                        .println("  • " + ctx.substring(0, Math.min(60, ctx.length())) + "..."));
                        System.out.println();
                        System.out.println("Generated Answer: " + item.actualOutputs().get("output"));
                        System.out.println("Status: " + (item.success() ? "✓ PASS" : "✗ FAIL"));
                        System.out.println("Evaluation Scores:");
                        item.evalResults().forEach(eval -> System.out.println("  • " + eval.name() + ": " +
                                        String.format("%.2f", eval.score()) +
                                        (eval.success() ? " ✓" : " ✗")));
                });
                System.out.println();
                System.out.println("=".repeat(70));
        }
}
