package dev.dokimos.core.conversation;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.JudgeLM;
import java.util.List;
import org.junit.jupiter.api.Test;

class LLMSimulatedUserTest {

    @Test
    void shouldGenerateMessageUsingLLM() {
        JudgeLM mockJudge = prompt -> "I need help with my order";

        SimulatedUser user = LLMSimulatedUser.builder()
                .judge(mockJudge)
                .persona("frustrated customer")
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.empty();
        Message message = user.generateMessage(trajectory);

        assertThat(message.role()).isEqualTo(Message.Role.USER);
        assertThat(message.content()).isEqualTo("I need help with my order");
    }

    @Test
    void shouldUseFixedResponsesFirst() {
        JudgeLM mockJudge = prompt -> "Dynamic response";

        SimulatedUser user = LLMSimulatedUser.builder()
                .judge(mockJudge)
                .fixedResponses(List.of("Fixed 1", "Fixed 2"))
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.empty();

        // First message should be fixed
        Message msg1 = user.generateMessage(trajectory);
        assertThat(msg1.content()).isEqualTo("Fixed 1");

        // After first user message
        trajectory = trajectory.withMessage(msg1).withMessage(Message.assistant("Response"));

        Message msg2 = user.generateMessage(trajectory);
        assertThat(msg2.content()).isEqualTo("Fixed 2");

        // After fixed responses are exhausted
        trajectory = trajectory.withMessage(msg2).withMessage(Message.assistant("Response 2"));

        Message msg3 = user.generateMessage(trajectory);
        assertThat(msg3.content()).isEqualTo("Dynamic response");
    }

    @Test
    void shouldIncludePersonaInPrompt() {
        String[] capturedPrompt = {null};

        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "Response";
        };

        SimulatedUser user = LLMSimulatedUser.builder()
                .judge(capturingJudge)
                .persona("angry customer demanding a refund")
                .build();

        user.generateMessage(ConversationTrajectory.empty());

        assertThat(capturedPrompt[0]).contains("angry customer demanding a refund");
    }

    @Test
    void shouldIncludeBehaviorGuidelinesInPrompt() {
        String[] capturedPrompt = {null};

        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "Response";
        };

        SimulatedUser user = LLMSimulatedUser.builder()
                .judge(capturingJudge)
                .behaviorGuidelines("Be assertive but polite")
                .build();

        user.generateMessage(ConversationTrajectory.empty());

        assertThat(capturedPrompt[0]).contains("Be assertive but polite");
    }

    @Test
    void shouldIncludeConversationHistoryInPrompt() {
        String[] capturedPrompt = {null};

        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "Response";
        };

        SimulatedUser user = LLMSimulatedUser.builder().judge(capturingJudge).build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Hello")
                .assistantMessage("Hi there!")
                .build();

        user.generateMessage(trajectory);

        assertThat(capturedPrompt[0]).contains("Hello");
        assertThat(capturedPrompt[0]).contains("Hi there!");
    }

    @Test
    void shouldIncludeScenarioInPrompt() {
        String[] capturedPrompt = {null};

        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "Response";
        };

        SimulatedUser user = LLMSimulatedUser.builder().judge(capturingJudge).build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Product return request")
                .build();

        user.generateMessage(trajectory);

        assertThat(capturedPrompt[0]).contains("Product return request");
    }

    @Test
    void shouldAllowCustomSystemPrompt() {
        String[] capturedPrompt = {null};

        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "Response";
        };

        SimulatedUser user = LLMSimulatedUser.builder()
                .judge(capturingJudge)
                .systemPrompt("Custom system prompt for testing")
                .build();

        user.generateMessage(ConversationTrajectory.empty());

        assertThat(capturedPrompt[0]).contains("Custom system prompt for testing");
    }

    @Test
    void shouldRequireJudge() {
        assertThatThrownBy(() -> LLMSimulatedUser.builder().persona("test").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM");
    }

    @Test
    void shouldTrimLLMResponse() {
        JudgeLM mockJudge = prompt -> "  Response with whitespace  \n";

        SimulatedUser user = LLMSimulatedUser.builder().judge(mockJudge).build();

        Message message = user.generateMessage(ConversationTrajectory.empty());

        assertThat(message.content()).isEqualTo("Response with whitespace");
    }

    @Test
    void shouldSurfaceClearExceptionWhenJudgeReturnsNull() {
        JudgeLM nullJudge = prompt -> null;

        LLMSimulatedUser user = LLMSimulatedUser.builder().judge(nullJudge).build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Previous user message")
                .assistantMessage("Assistant reply")
                .build();

        assertThatThrownBy(() -> user.generateMessage(trajectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM")
                .hasMessageContaining("null");
    }
}
