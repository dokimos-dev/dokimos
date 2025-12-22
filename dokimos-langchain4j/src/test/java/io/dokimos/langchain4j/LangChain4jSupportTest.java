package io.dokimos.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import io.dokimos.core.Example;
import io.dokimos.core.JudgeLM;
import io.dokimos.core.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * This class tests the {@link LangChain4jSupport} class, which contains support utilities for integrating with LangChain4j.
 */
class LangChain4jSupportTest {

    @Test
    void asJudge_shouldDelegateToChatModel() {
        ChatModel mockModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("Judge response"))
                        .build();
            }
        };

        var judge = LangChain4jSupport.asJudge(mockModel);

        String response = judge.generate("Test prompt");
        assertThat(response).isEqualTo("Judge response");
    }

    @Test
    void simpleTask_shouldExtractInputAndProduceOutputs() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("The answer is 47"))
                        .build();
            }
        };

        var task = LangChain4jSupport.simpleTask(chatModel);

        var example = Example.of("What is the answer of 45+2?", "47");
        Map<String, Object> outputs = task.run(example);

        assertThat(outputs).containsEntry("output", "The answer is 47");
    }

    @Test
    void ragTask_shouldExtractContextFromSources() {
        List<Content> sources = List.of(
                Content.from(TextSegment.from("90-day money-back guarantee")),
                Content.from(TextSegment.from("Contact our support for more information about refunds"))
        );

        Result<String> mockResult = Result.<String>builder()
                .content("You can get a refund within 90 days after purchase.")
                .sources(sources)
                .build();

        var task = LangChain4jSupport.ragTask(input -> mockResult);

        var example = Example.of("What is the refund policy?", "90 days");
        Map<String, Object> outputs = task.run(example);

        assertThat(outputs).containsEntry("output", "You can get a refund within 90 days after purchase.");
        assertThat(outputs).containsKey("context");

        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) outputs.get("context");
        assertThat(context).containsExactly(
                "90-day money-back guarantee",
                "Contact our support for more information about refunds"
        );
    }

    @Test
    void ragTask_shouldSupportCustomKeys() {
        List<Content> sources = List.of(Content.from(TextSegment.from("Source document")));

        Result<String> mockResult = Result.<String>builder()
                .content("The answer")
                .sources(sources)
                .build();

        var task = LangChain4jSupport.ragTask(
                input -> mockResult,
                "question",
                "answer",
                "documentContext"
        );

        var example = Example.builder()
                .input("question", "What?")
                .build();

        Map<String, Object> outputs = task.run(example);

        assertThat(outputs).containsKey("answer");
        assertThat(outputs).containsKey("documentContext");
        assertThat(outputs).doesNotContainKey("output");
        assertThat(outputs).doesNotContainKey("context");
        assertThat(outputs).doesNotContainKey("retrievalContext");
    }

    @Test
    void extractTexts_shouldHandleNullSources() {
        List<String> texts = LangChain4jSupport.extractTexts(null);
        assertThat(texts).isEmpty();
    }

    @Test
    void extractTexts_shouldHandleEmptySources() {
        List<String> texts = LangChain4jSupport.extractTexts(List.of());
        assertThat(texts).isEmpty();
    }

    @Test
    void extractTexts_shouldExtractTextFromContents() {
        List<Content> contents = List.of(
                Content.from(TextSegment.from("First segment")),
                Content.from(TextSegment.from("Second segment"))
        );

        List<String> texts = LangChain4jSupport.extractTexts(contents);

        assertThat(texts).containsExactly("First segment", "Second segment");
    }

    @Test
    void extractTextsWithMetadata_shouldIncludeMetadata() {
        List<Content> contents = List.of(
                Content.from(TextSegment.from(
                        "Document content",
                        dev.langchain4j.data.document.Metadata.from("source", "G://files/test-file.md")
                ))
        );

        List<Map<String, Object>> results = LangChain4jSupport.extractTextsWithMetadata(contents);

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("text", "Document content");
        assertThat(results.get(0)).containsKey("metadata");

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) results.get(0).get("metadata");
        assertThat(metadata).containsEntry("source", "G://files/test-file.md");
    }

    @Test
    void customTask_shouldSupportFullControl() {
        var task = LangChain4jSupport.customTask(example -> {
            return Map.of(
                    "output", "The AI generated response",
                    "context", List.of("doc1", "doc2"),
                    "latencyMs", 150L,
                    "customMetric", 0.95
            );
        });

        var example = Example.of("What?", "Some answer");
        Map<String, Object> outputs = task.run(example);

        assertThat(outputs).containsEntry("output", "The AI generated response");
        assertThat(outputs).containsEntry("context", List.of("doc1", "doc2"));
        assertThat(outputs).containsEntry("latencyMs", 150L);
        assertThat(outputs).containsEntry("customMetric", 0.95);
    }

    @Test
    void ragTask_shouldHandleNullSources() {
        Result<String> mockResult = Result.<String>builder()
                .content("The Answer without sources")
                .sources(null)
                .build();

        var task = LangChain4jSupport.ragTask(input -> mockResult);

        var example = Example.of("Question?", "Expected answer");
        Map<String, Object> outputs = task.run(example);

        assertThat(outputs).containsEntry("output", "The Answer without sources");

        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) outputs.get("context");
        assertThat(context).isEmpty();
    }
}
