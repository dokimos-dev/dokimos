package dev.dokimos.springai;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.AsyncTask;
import dev.dokimos.core.Example;
import dev.dokimos.core.TaskResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Mono;

/**
 * Tests for the async and reactive {@link AsyncTask} factory methods on {@link SpringAiSupport}.
 */
class SpringAiAsyncTaskTest {

    @Test
    void asyncTask_shouldCompleteFutureWithChatClientResponse() throws Exception {
        ChatModel mockModel =
                prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("Async response"))));
        ChatClient client = ChatClient.builder(mockModel).build();

        AsyncTask task = SpringAiSupport.asyncTask(client);

        TaskResult result = task.run(Example.of("What is 2+2?", "4")).get();

        assertThat(result.outputs()).containsEntry("output", "Async response");
    }

    @Test
    void asyncTask_shouldSendInputAsUserMessage() throws Exception {
        final String[] captured = {null};
        ChatModel mockModel = prompt -> {
            captured[0] = prompt.getInstructions().get(0).getText();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };
        ChatClient client = ChatClient.builder(mockModel).build();

        AsyncTask task = SpringAiSupport.asyncTask(client);
        task.run(Example.of("my question", "answer")).get();

        assertThat(captured[0]).isEqualTo("my question");
    }

    @Test
    void asyncTask_shouldSupportCustomKeys() throws Exception {
        ChatModel mockModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("response"))));
        ChatClient client = ChatClient.builder(mockModel).build();

        AsyncTask task = SpringAiSupport.asyncTask(client, "question", "answer");

        var example = Example.builder().input("question", "What?").build();
        TaskResult result = task.run(example).get();

        assertThat(result.outputs()).containsEntry("answer", "response");
        assertThat(result.outputs()).doesNotContainKey("output");
    }

    @Test
    void asyncTask_shouldCompleteExceptionallyWhenClientThrows() {
        ChatModel mockModel = prompt -> {
            throw new IllegalStateException("model down");
        };
        ChatClient client = ChatClient.builder(mockModel).build();

        AsyncTask task = SpringAiSupport.asyncTask(client);

        assertThatThrownBy(() -> task.run(Example.of("q", "a")).get())
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model down");
    }

    @Test
    void asyncTask_shouldRejectNullClient() {
        assertThatThrownBy(() -> SpringAiSupport.asyncTask(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reactiveTask_shouldAdaptMonoOfTaskResultToFuture() throws Exception {
        TaskResult expected = TaskResult.of(Map.of("output", "reactive value"));

        AsyncTask task = SpringAiSupport.reactiveTask(example -> Mono.just(expected));

        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(result.outputs()).containsEntry("output", "reactive value");
    }

    @Test
    void reactiveTask_shouldReceiveTheExample() throws Exception {
        AsyncTask task = SpringAiSupport.reactiveTask(
                example -> Mono.just(TaskResult.of(Map.of("output", example.input()))));

        TaskResult result = task.run(Example.of("echo me", "a")).get();

        assertThat(result.outputs()).containsEntry("output", "echo me");
    }

    @Test
    void reactiveTask_shouldPropagateMonoError() {
        AsyncTask task = SpringAiSupport.reactiveTask(
                example -> Mono.error(new IllegalStateException("boom")));

        assertThatThrownBy(() -> task.run(Example.of("q", "a")).get())
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void reactiveTask_shouldRejectNullFunction() {
        assertThatThrownBy(() -> SpringAiSupport.reactiveTask(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reactiveStringTask_shouldWrapEmittedStringUnderOutputKey() throws Exception {
        AsyncTask task = SpringAiSupport.reactiveStringTask(example -> Mono.just("hello"));

        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(result.outputs()).containsEntry("output", "hello");
    }

    @Test
    void reactiveStringTask_shouldYieldEmptyStringForEmptyMono() throws Exception {
        AsyncTask task = SpringAiSupport.reactiveStringTask(example -> Mono.empty());

        TaskResult result = task.run(Example.of("q", "a")).get();

        assertThat(result.outputs()).containsEntry("output", "");
    }

    @Test
    void reactiveStringTask_shouldRejectNullFunction() {
        assertThatThrownBy(() -> SpringAiSupport.reactiveStringTask(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
