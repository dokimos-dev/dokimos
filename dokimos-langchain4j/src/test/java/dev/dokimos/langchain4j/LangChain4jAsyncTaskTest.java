package dev.dokimos.langchain4j;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.AsyncTask;
import dev.dokimos.core.Example;
import dev.dokimos.core.TaskResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/**
 * Tests for the async {@link AsyncTask} factory methods on {@link LangChain4jSupport}.
 */
class LangChain4jAsyncTaskTest {

    @Test
    void asyncRagTask_shouldCompleteFutureWithOutputAndContext() throws Exception {
        List<Content> sources = List.of(
                Content.from(TextSegment.from("90-day money-back guarantee")),
                Content.from(TextSegment.from("Contact support for refunds")));

        Result<String> mockResult = Result.<String>builder()
                .content("You can get a refund within 90 days.")
                .sources(sources)
                .build();

        AsyncTask task = LangChain4jSupport.asyncRagTask(input -> mockResult);

        var example = Example.of("What is the refund policy?", "90 days");
        TaskResult result = task.run(example).get();

        assertThat(result.outputs()).containsEntry("output", "You can get a refund within 90 days.");

        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) result.outputs().get("context");
        assertThat(context).containsExactly("90-day money-back guarantee", "Contact support for refunds");
    }

    @Test
    void asyncRagTask_passesNullContentThroughUnderTheOutputKey() throws Exception {
        // Contract pin: asyncRagTask (like the synchronous ragTask) writes result.content() into a
        // HashMap with no null-coercion, so a null-content Result surfaces output == null. This is the
        // RAG contract — deliberately distinct from asyncTask/simpleTask, which coerce null to "".
        // Asserting it makes the asymmetry intentional: a regression that started coercing (or that
        // switched to Map.of and NPE'd) would change this and fail here.
        Result<String> mockResult =
                Result.<String>builder().content(null).sources(List.of()).build();

        AsyncTask task = LangChain4jSupport.asyncRagTask(input -> mockResult);

        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(result.outputs()).containsKey("output");
        assertThat(result.outputs().get("output")).isNull();
    }

    @Test
    void asyncRagTask_shouldSupportCustomKeys() throws Exception {
        List<Content> sources = List.of(Content.from(TextSegment.from("Source document")));

        Result<String> mockResult =
                Result.<String>builder().content("The answer").sources(sources).build();

        AsyncTask task = LangChain4jSupport.asyncRagTask(input -> mockResult, "question", "answer", "documentContext");

        var example = Example.builder().input("question", "What?").build();
        TaskResult result = task.run(example).get();

        assertThat(result.outputs()).containsKey("answer");
        assertThat(result.outputs()).containsKey("documentContext");
        assertThat(result.outputs()).doesNotContainKey("output");
        assertThat(result.outputs()).doesNotContainKey("context");
    }

    @Test
    void asyncRagTask_shouldPassInputToAssistantCall() throws Exception {
        final String[] captured = {null};
        Result<String> mockResult =
                Result.<String>builder().content("ok").sources(List.of()).build();

        AsyncTask task = LangChain4jSupport.asyncRagTask(input -> {
            captured[0] = input;
            return mockResult;
        });

        task.run(Example.of("the question", "the answer")).get();

        assertThat(captured[0]).isEqualTo("the question");
    }

    @Test
    void asyncRagTask_shouldCompleteExceptionallyWhenAssistantThrows() {
        AsyncTask task = LangChain4jSupport.asyncRagTask(input -> {
            throw new IllegalStateException("model down");
        });

        assertThatThrownBy(() -> task.run(Example.of("q", "a")).get())
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model down");
    }

    @Test
    void asyncRagTask_shouldRejectNullAssistantCall() {
        assertThatThrownBy(() -> LangChain4jSupport.asyncRagTask(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asyncTask_shouldCompleteFutureWithChatOutput() throws Exception {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("The answer is 47"))
                        .build();
            }
        };

        AsyncTask task = LangChain4jSupport.asyncTask(chatModel);

        TaskResult result = task.run(Example.of("What is 45+2?", "47")).get();

        assertThat(result.outputs()).containsEntry("output", "The answer is 47");
    }

    @Test
    void asyncTask_shouldStoreEmptyStringWhenChatReturnsNull() throws Exception {
        // A model that yields null (e.g. an empty/tool-only completion) must be coerced to "" by the
        // null-guard rather than passed to Map.of(...), which would NPE on the worker thread.
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public String chat(String userMessage) {
                return null;
            }
        };

        AsyncTask task = LangChain4jSupport.asyncTask(chatModel);

        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(result.outputs()).containsEntry("output", "");
    }

    @Test
    void asyncTask_shouldSupportCustomOutputKey() throws Exception {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response"))
                        .build();
            }
        };

        AsyncTask task = LangChain4jSupport.asyncTask(chatModel, "answer");

        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(result.outputs()).containsEntry("answer", "response");
        assertThat(result.outputs()).doesNotContainKey("output");
    }

    @Test
    void asyncTask_shouldRejectNullModel() {
        assertThatThrownBy(() -> LangChain4jSupport.asyncTask(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asyncTask_shouldRunOnProvidedExecutor() throws Exception {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
            }
        };
        java.util.concurrent.atomic.AtomicInteger used = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.Executor executor = command -> {
            used.incrementAndGet();
            command.run();
        };

        AsyncTask task = LangChain4jSupport.asyncTask(chatModel, executor);
        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(used.get()).isEqualTo(1);
        assertThat(result.outputs()).containsEntry("output", "ok");
    }

    @Test
    void asyncRagTask_shouldRunOnProvidedExecutor() throws Exception {
        Result<String> mockResult =
                Result.<String>builder().content("answer").sources(List.of()).build();
        java.util.concurrent.atomic.AtomicInteger used = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.Executor executor = command -> {
            used.incrementAndGet();
            command.run();
        };

        AsyncTask task = LangChain4jSupport.asyncRagTask(input -> mockResult, executor);
        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(used.get()).isEqualTo(1);
        assertThat(result.outputs()).containsEntry("output", "answer");
    }

    @Test
    void asyncTask_shouldRejectNullExecutor() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
            }
        };

        assertThatThrownBy(() -> LangChain4jSupport.asyncTask(chatModel, (java.util.concurrent.Executor) null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
