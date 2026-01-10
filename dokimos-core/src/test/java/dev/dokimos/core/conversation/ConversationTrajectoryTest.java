package dev.dokimos.core.conversation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ConversationTrajectoryTest {

    @Test
    void shouldCreateEmptyTrajectory() {
        ConversationTrajectory trajectory = ConversationTrajectory.empty();

        assertThat(trajectory.messages()).isEmpty();
        assertThat(trajectory.scenario()).isEmpty();
        assertThat(trajectory.metadata()).isEmpty();
        assertThat(trajectory.isEmpty()).isTrue();
        assertThat(trajectory.turnCount()).isZero();
    }

    @Test
    void shouldBuildTrajectoryWithMessages() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Customer support")
                .userMessage("I need help")
                .assistantMessage("How can I assist you?")
                .userMessage("I have a problem")
                .assistantMessage("Let me help with that")
                .build();

        assertThat(trajectory.messages()).hasSize(4);
        assertThat(trajectory.scenario()).isEqualTo("Customer support");
        assertThat(trajectory.turnCount()).isEqualTo(2);
        assertThat(trajectory.isEmpty()).isFalse();
    }

    @Test
    void shouldFilterUserMessages() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("User 1")
                .assistantMessage("Assistant 1")
                .userMessage("User 2")
                .build();

        List<Message> userMessages = trajectory.userMessages();

        assertThat(userMessages).hasSize(2);
        assertThat(userMessages).allMatch(Message::isUser);
    }

    @Test
    void shouldFilterAssistantMessages() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("User 1")
                .assistantMessage("Assistant 1")
                .assistantMessage("Assistant 2")
                .build();

        List<Message> assistantMessages = trajectory.assistantMessages();

        assertThat(assistantMessages).hasSize(2);
        assertThat(assistantMessages).allMatch(Message::isAssistant);
    }

    @Test
    void shouldGetLastMessage() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("First")
                .assistantMessage("Last")
                .build();

        assertThat(trajectory.lastMessage().content()).isEqualTo("Last");
    }

    @Test
    void shouldReturnNullForLastMessageWhenEmpty() {
        ConversationTrajectory trajectory = ConversationTrajectory.empty();

        assertThat(trajectory.lastMessage()).isNull();
        assertThat(trajectory.lastUserMessage()).isNull();
        assertThat(trajectory.lastAssistantMessage()).isNull();
    }

    @Test
    void shouldAppendMessageImmutably() {
        ConversationTrajectory original = ConversationTrajectory.builder()
                .userMessage("Original")
                .build();

        ConversationTrajectory updated = original.withMessage(Message.assistant("New"));

        assertThat(original.messages()).hasSize(1);
        assertThat(updated.messages()).hasSize(2);
        assertThat(updated.lastMessage().content()).isEqualTo("New");
    }

    @Test
    void shouldCalculateTurnCountCorrectly() {
        // A turn is a user message followed by an assistant response
        ConversationTrajectory incomplete = ConversationTrajectory.builder()
                .userMessage("U1")
                .assistantMessage("A1")
                .userMessage("U2") // No response yet
                .build();

        // min(2 users, 1 assistant) = 1
        assertThat(incomplete.turnCount()).isEqualTo(1);

        ConversationTrajectory complete = ConversationTrajectory.builder()
                .userMessage("U1")
                .assistantMessage("A1")
                .userMessage("U2")
                .assistantMessage("A2")
                .build();

        assertThat(complete.turnCount()).isEqualTo(2);
    }

    @Test
    void shouldConvertToText() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Test scenario")
                .userMessage("Hello")
                .assistantMessage("Hi there")
                .build();

        String text = trajectory.toText();

        assertThat(text).contains("Scenario: Test scenario");
        assertThat(text).contains("USER: Hello");
        assertThat(text).contains("ASSISTANT: Hi there");
    }

    @Test
    void shouldConvertToJson() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Test")
                .userMessage("Hello")
                .build();

        String json = trajectory.toJson();

        assertThat(json).contains("\"scenario\"");
        assertThat(json).contains("\"Test\"");
        assertThat(json).contains("\"messages\"");
        assertThat(json).contains("\"role\"");
        assertThat(json).contains("\"user\"");
        assertThat(json).contains("\"content\"");
        assertThat(json).contains("\"Hello\"");
    }

    @Test
    void shouldAddMetadata() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .metadata("key1", "value1")
                .metadata(Map.of("key2", "value2"))
                .build();

        assertThat(trajectory.metadata())
                .containsEntry("key1", "value1")
                .containsEntry("key2", "value2");
    }

    @Test
    void shouldMakeMessagesImmutable() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Test")
                .build();

        assertThatThrownBy(() -> trajectory.messages().add(Message.user("New")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
