package dev.dokimos.examples.langchain4j;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.dokimos.core.*;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

/**
 * Simple RAG evaluation example using LangChain4j with local embeddings.
 * Requires OPENAI_API_KEY for the used chat model and LLM judge implementation.
 */
public class LangChain4jRAGExample {

    public static void main(String[] args) {
        // Validate the OPENAI_API_KEY env variable
        if (System.getenv("OPENAI_API_KEY") == null) {
            System.err.println("OPENAI_API_KEY not set");
            System.exit(1);
        }

        var embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        var embeddingStore = new InMemoryEmbeddingStore<TextSegment>();

        var documents = List.of(
                Document.from("We offer a 30-day money-back guarantee on all purchases."),
                Document.from("Standard shipping takes 5-7 business days."),
                Document.from("All products come with a 1-year warranty.")
        );

        EmbeddingStoreIngestor.builder()
                .documentSplitter(recursive(200, 20))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(documents);

        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(OpenAiChatModel.builder()
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .modelName(OpenAiChatModelName.GPT_5_NANO)
                        .build())
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .maxResults(2)
                        .build())
                .build();

        Dataset dataset = Dataset.builder()
                .name("customer-qa")
                .addExample(Example.of("What is the refund policy?", "30-day money-back guarantee"))
                .addExample(Example.of("How long does shipping take?", "5-7 business days"))
                .build();

        JudgeLM judge = prompt -> {
            var params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(ChatModel.GPT_4O_MINI)
                    .build();
            return client.chat().completions().create(params)
                    .choices().get(0).message().content().orElse("");
        };

        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder()
                        .threshold(0.5)
                        .build(),
                LLMJudgeEvaluator.builder()
                        .name("Answer Relevance")
                        .judge(judge)
                        .criteria("Is the answer relevant and accurate?")
                        .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
                        .threshold(0.7)
                        .build(),
                FaithfulnessEvaluator.builder()
                        .threshold(0.7)
                        .judge(judge)
                        .build()
        );

        ExperimentResult result = Experiment.builder()
                .name("RAG Evaluation")
                .dataset(dataset)
                .task(LangChain4jSupport.ragTask(assistant::chat))
                .evaluators(evaluators)
                .build()
                .run();

        System.out.println("Pass rate: " + String.format("%.0f%%", result.passRate() * 100));
        System.out.println("\nResults:");
        result.itemResults().forEach(item -> {
            System.out.println("\nQ: " + item.example().input());
            System.out.println("A: " + item.actualOutputs().get("output"));
            System.out.println("Status: " + (item.success() ? "PASS" : "FAIL"));
            item.evalResults().forEach(eval ->
                    System.out.println("  " + eval.name() + ": " + String.format("%.2f", eval.score())));
        });
    }

    interface Assistant {
        Result<String> chat(String userMessage);
    }

}
