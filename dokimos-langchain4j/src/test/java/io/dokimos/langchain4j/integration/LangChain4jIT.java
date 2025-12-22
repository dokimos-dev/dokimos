package io.dokimos.langchain4j.integration;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.dokimos.core.*;
import io.dokimos.langchain4j.LangChain4jSupport;
import org.junit.jupiter.api.BeforeAll;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;
import static org.assertj.core.api.Assertions.assertThat;


class LangChain4jIT {

    private static final List<Document> KNOWLEDGE_BASE = List.of(
            Document.from(
                    """
                            Refund Policy
                            
                            We offer a 30-day money-back guarantee on all purchases.
                            To request a refund, contact our support team with your order number.
                            Refunds are processed within 5-7 business days.
                            Digital products are eligible for refunds only if not downloaded.
                            """,
                    Metadata.from("source", "refund-policy.md")
            ),
            Document.from(
                    """
                            Shipping Information
                            
                            Standard shipping takes 5-7 business days within the continental US.
                            Express shipping (2-3 days) is available for an additional $15.
                            International shipping times vary by destination, typically 10-21 days.
                            Free shipping on orders over $50.
                            """,
                    Metadata.from("source", "shipping.md")
            ),
            Document.from(
                    """
                            Product Warranty
                            
                            All products come with a 1-year manufacturer warranty.
                            The warranty covers defects in materials and workmanship.
                            Warranty does not cover damage from misuse or accidents.
                            Extended warranty (3 years) available for purchase.
                            """,
                    Metadata.from("source", "warranty.md")
            ),
            Document.from(
                    """
                            Return Process
                            
                            To return an item, first request a Return Merchandise Authorization (RMA).
                            Pack the item in its original packaging if possible.
                            Include the RMA number on the outside of the package.
                            Ship to our returns center within 14 days of receiving the RMA.
                            We provide prepaid return labels for defective items.
                            """,
                    Metadata.from("source", "returns.md")
            )
    );
    private static EmbeddingModel embeddingModel;
    private static EmbeddingStore<TextSegment> embeddingStore;

    @BeforeAll
    static void setup() {
        embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        embeddingStore = new InMemoryEmbeddingStore<>();

        // Ingest the documents and build the vector store
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(recursive(200, 20))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(KNOWLEDGE_BASE);
    }

    /**
     * Test a basic/naive RAG approach that uses {@code Result.sources()}.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void shouldSupportNaiveRag() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_5_NANO)
                .build();

        AIAssistant assistant = AiServices.builder(AIAssistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .queryTransformer(CompressingQueryTransformer.builder()
                                .chatModel(chatModel)
                                .build())
                        .contentRetriever(EmbeddingStoreContentRetriever.builder()
                                .embeddingStore(embeddingStore)
                                .embeddingModel(embeddingModel)
                                .maxResults(3)
                                .build())
                        .build())
                .build();

        var task = LangChain4jSupport.ragTask(assistant::chat);

        var dataset = Dataset.builder()
                .name("customer-support-qa")
                .description("Questions about refunds, and warranties")
                .addExample(Example.builder()
                        .input("input", "What is the refund policy?")
                        .expectedOutput("output", "30-day money-back guarantee")
                        .build())
                .addExample(Example.builder()
                        .input("input", "How long does shipping take?")
                        .expectedOutput("output", "5-7 business days for standard shipping")
                        .build())
                .addExample(Example.builder()
                        .input("input", "Is there a warranty on products?")
                        .expectedOutput("output", "1-year manufacturer warranty")
                        .build())
                .build();

        JudgeLM judge = LangChain4jSupport.asJudge(chatModel);

        Evaluator answerRelevanceEvaluator = LLMJudgeEvaluator.builder()
                .name("Answer Relevance")
                .judge(judge)
                .criteria("Is the answer relevant to the user's question?")
                .evaluationParams(List.of(
                        EvalTestCaseParam.INPUT,
                        EvalTestCaseParam.ACTUAL_OUTPUT
                ))
                .threshold(0.8)
                .build();

        ExperimentResult result = Experiment.builder()
                .name("rag-qa-assistant-v0")
                .description("Evaluating the RAG assistant with query compression and basic retrieval")
                .dataset(dataset)
                .task(task)
                .evaluator(answerRelevanceEvaluator)
                .metadata("model", "gpt-5-nano")
                .metadata("embedding-model", "bge-small-en-v1.5")
                .build()
                .run();

        assertThat(result.itemResults()).hasSize(3);
        assertThat(result.itemResults().getFirst().success()).isTrue();
    }

    interface AIAssistant {
        Result<String> chat(String userMessage);
    }

}
