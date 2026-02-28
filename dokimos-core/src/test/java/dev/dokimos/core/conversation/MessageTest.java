package dev.dokimos.core.conversation;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void shouldCreateUserMessage() {
        Message message = Message.user("Hello");

        assertThat(message.role()).isEqualTo(Message.Role.USER);
        assertThat(message.content()).isEqualTo("Hello");
        assertThat(message.metadata()).isEmpty();
        assertThat(message.isUser()).isTrue();
        assertThat(message.isAssistant()).isFalse();
        assertThat(message.isSystem()).isFalse();
    }

    @Test
    void shouldCreateAssistantMessage() {
        Message message = Message.assistant("Hi there!");

        assertThat(message.role()).isEqualTo(Message.Role.ASSISTANT);
        assertThat(message.content()).isEqualTo("Hi there!");
        assertThat(message.isAssistant()).isTrue();
        assertThat(message.isUser()).isFalse();
    }

    @Test
    void shouldCreateSystemMessage() {
        Message message = Message.system("You are a helpful assistant.");

        assertThat(message.role()).isEqualTo(Message.Role.SYSTEM);
        assertThat(message.content()).isEqualTo("You are a helpful assistant.");
        assertThat(message.isSystem()).isTrue();
    }

    @Test
    void shouldCreateMessageWithMetadata() {
        Message message = new Message(Message.Role.USER, "Test", Map.of("timestamp", "2024-01-01"));

        assertThat(message.metadata()).containsEntry("timestamp", "2024-01-01");
    }

    @Test
    void shouldCreateMessageWithOfFactory() {
        Message message = Message.of(Message.Role.ASSISTANT, "Response");

        assertThat(message.role()).isEqualTo(Message.Role.ASSISTANT);
        assertThat(message.content()).isEqualTo("Response");
        assertThat(message.metadata()).isEmpty();
    }

    @Test
    void shouldRejectNullRole() {
        assertThatThrownBy(() -> new Message(null, "content", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role cannot be null");
    }

    @Test
    void shouldRejectNullContent() {
        assertThatThrownBy(() -> new Message(Message.Role.USER, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content cannot be null");
    }

    @Test
    void shouldHandleNullMetadataGracefully() {
        Message message = new Message(Message.Role.USER, "Test", null);

        assertThat(message.metadata()).isEmpty();
    }

    @Test
    void shouldMakeMetadataImmutable() {
        Message message = Message.user("Test");

        assertThatThrownBy(() -> message.metadata().put("key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
