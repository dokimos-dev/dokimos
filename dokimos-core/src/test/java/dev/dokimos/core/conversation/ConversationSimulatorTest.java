package dev.dokimos.core.conversation;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.agents.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConversationSimulatorTest {

    @Test
    void shouldSimulateBasicConversation() {
        SimulatedUser user = trajectory ->
                Message.user("User turn " + (trajectory.userMessages().size() + 1));

        ConversationalApplication app = trajectory -> Message.assistant("Assistant response");

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(3)
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        assertThat(trajectory.messages()).hasSize(6); // 3 user + 3 assistant
        assertThat(trajectory.turnCount()).isEqualTo(3);
    }

    @Test
    void shouldUseInitialMessage() {
        SimulatedUser user = trajectory -> Message.user("Generated message");

        ConversationalApplication app = trajectory -> Message.assistant("Response");

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(2)
                .initialMessage("Custom initial message")
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        assertThat(trajectory.messages().get(0).content()).isEqualTo("Custom initial message");
        assertThat(trajectory.messages().get(2).content()).isEqualTo("Generated message");
    }

    @Test
    void shouldSetScenario() {
        SimulatedUser user = trajectory -> Message.user("Message");
        ConversationalApplication app = trajectory -> Message.assistant("Response");

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(1)
                .scenario("Customer complaint handling")
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        assertThat(trajectory.scenario()).isEqualTo("Customer complaint handling");
    }

    @Test
    void shouldStopOnCondition() {
        AtomicInteger turnCount = new AtomicInteger(0);

        SimulatedUser user = trajectory -> {
            turnCount.incrementAndGet();
            return Message.user("User message");
        };

        ConversationalApplication app = trajectory -> {
            if (trajectory.userMessages().size() >= 2) {
                return Message.assistant("Goodbye!");
            }
            return Message.assistant("Continue...");
        };

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(10)
                .stoppingCondition(trajectory -> {
                    Message last = trajectory.lastAssistantMessage();
                    return last != null && last.content().contains("Goodbye");
                })
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        assertThat(trajectory.turnCount()).isEqualTo(2);
        assertThat(trajectory.lastAssistantMessage().content()).contains("Goodbye");
    }

    @Test
    void shouldRespectMaxTurns() {
        SimulatedUser user = trajectory -> Message.user("Message");
        ConversationalApplication app = trajectory -> Message.assistant("Response");

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(5)
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        assertThat(trajectory.turnCount()).isEqualTo(5);
    }

    @Test
    void shouldRequireSimulatedUser() {
        ConversationalApplication app = trajectory -> Message.assistant("Response");

        assertThatThrownBy(
                        () -> ConversationSimulator.builder().application(app).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SimulatedUser");
    }

    @Test
    void shouldRequireApplication() {
        SimulatedUser user = trajectory -> Message.user("Message");

        assertThatThrownBy(() ->
                        ConversationSimulator.builder().simulatedUser(user).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ConversationalApplication");
    }

    @Test
    void shouldRejectInvalidMaxTurns() {
        assertThatThrownBy(() -> ConversationSimulator.builder().maxTurns(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTurns");
    }

    @Test
    void shouldSupportAsyncSimulation() throws Exception {
        SimulatedUser user = trajectory -> Message.user("Message");
        ConversationalApplication app = trajectory -> Message.assistant("Response");

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(2)
                .build();

        ConversationTrajectory trajectory = simulator.simulateAsync().get();

        assertThat(trajectory.turnCount()).isEqualTo(2);
    }

    @Test
    void shouldPassTrajectoryContextToSimulatedUser() {
        SimulatedUser user = trajectory -> {
            if (trajectory.isEmpty()) {
                return Message.user("First message");
            }
            Message lastAssistant = trajectory.lastAssistantMessage();
            return Message.user("Responding to: " + lastAssistant.content());
        };

        ConversationalApplication app = trajectory ->
                Message.assistant("Reply #" + trajectory.assistantMessages().size());

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(2)
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        assertThat(trajectory.messages().get(0).content()).isEqualTo("First message");
        assertThat(trajectory.messages().get(2).content()).isEqualTo("Responding to: Reply #0");
    }

    @Test
    void shouldReturnPartialTrajectoryWhenSimulatedUserThrowsOnSecondTurn() {
        SimulatedUser user = trajectory -> {
            if (trajectory.userMessages().size() >= 1) {
                throw new RuntimeException("simulated user blew up on turn 2");
            }
            return Message.user("First user message");
        };

        ConversationalApplication app = trajectory -> Message.assistant("Assistant reply");

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(5)
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        // First turn is preserved instead of losing the entire trajectory
        assertThat(trajectory.turnCount()).isEqualTo(1);
        assertThat(trajectory.userMessages()).hasSize(1);
        assertThat(trajectory.userMessages().get(0).content()).isEqualTo("First user message");
        assertThat(trajectory.metadata()).containsEntry("errorSource", "simulatedUser");
        assertThat(trajectory.metadata().get("error").toString()).contains("turn 2");
    }

    @Test
    void shouldPassThroughPerTurnToolCallsIntoFlatTrajectory() {
        ToolCall search = ToolCall.of("search", Map.of("q", "weather"));
        ToolCall book = ToolCall.of("book", Map.of("id", "42"));

        SimulatedUser user = trajectory -> Message.user("User message");

        ConversationalApplication app = trajectory -> {
            // Turn 1 calls one tool, turn 2 calls another.
            List<ToolCall> calls = trajectory.assistantMessages().isEmpty() ? List.of(search) : List.of(book);
            return Message.assistant("Assistant reply", calls);
        };

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(2)
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        // toolCalls() is the chronological concatenation of every assistant turn's calls.
        assertThat(trajectory.toolCalls()).containsExactly(search, book);
        assertThat(trajectory.toolCallsByTurn()).containsExactly(List.of(search), List.of(book));
    }

    @Test
    void shouldPreserveToolCallsGatheredSoFarWhenApplicationThrowsMidConversation() {
        ToolCall search = ToolCall.of("search", Map.of("q", "weather"));

        SimulatedUser user = trajectory -> Message.user("User message");

        ConversationalApplication app = trajectory -> {
            // First turn carries tool calls; the second turn blows up mid-conversation.
            if (trajectory.assistantMessages().isEmpty()) {
                return Message.assistant("Assistant reply", List.of(search));
            }
            throw new RuntimeException("application blew up on turn 2");
        };

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(5)
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        // The partial trajectory still exposes the calls gathered before the failure.
        assertThat(trajectory.toolCalls()).containsExactly(search);
        assertThat(trajectory.metadata()).containsEntry("errorSource", "application");
        assertThat(trajectory.metadata().get("error").toString()).contains("turn 2");
    }

    @Test
    void shouldSurfaceClearErrorWhenApplicationReturnsNull() {
        SimulatedUser user = trajectory -> Message.user("User message");
        ConversationalApplication app = trajectory -> null;

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .maxTurns(3)
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        assertThat(trajectory.userMessages()).hasSize(1);
        assertThat(trajectory.metadata()).containsEntry("errorSource", "application");
        assertThat(trajectory.metadata().get("error").toString()).contains("null");
    }
}
