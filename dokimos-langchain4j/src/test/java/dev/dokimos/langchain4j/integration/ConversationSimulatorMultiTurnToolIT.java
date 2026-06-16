package dev.dokimos.langchain4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.conversation.ConversationSimulator;
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.conversation.ConversationalApplication;
import dev.dokimos.core.conversation.LLMSimulatedUser;
import dev.dokimos.core.conversation.Message;
import dev.dokimos.core.conversation.SimulatedUser;
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator;
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator;
import dev.dokimos.core.evaluators.agents.ToolErrorEvaluator;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Drives a live multi-turn tool-calling conversation through {@link ConversationSimulator} with a
 * real OpenAI-backed {@link LLMSimulatedUser} and a real LangChain4j {@code AiService}. The per-turn
 * evaluators score every assistant turn, and {@link TaskCompletionEvaluator} is shown to
 * discriminate: one asked task (email) has no tool, so the run cannot score 1.0. Requires
 * {@code OPENAI_API_KEY}.
 */
@Tag("integration")
class ConversationSimulatorMultiTurnToolIT {

    /** In-memory store. completeTask matching is tolerant since the task text flows through an LLM. */
    static class TaskStore {

        private final Map<String, Boolean> tasks = new LinkedHashMap<>();

        @Tool("Add a new task to the user's to-do list")
        String addTask(@P("the task description") String task) {
            tasks.put(task, false);
            return "{\"added\": \"" + task + "\"}";
        }

        @Tool("Mark an existing task as done")
        String completeTask(@P("the task description to complete") String task) {
            String key = resolve(task);
            String target = key != null ? key : task;
            tasks.put(target, true);
            return "{\"completed\": \"" + target + "\"}";
        }

        @Tool("List all tasks with their done/not-done status")
        String listTasks() {
            StringBuilder sb = new StringBuilder("{\"tasks\": [");
            boolean first = true;
            for (Map.Entry<String, Boolean> entry : tasks.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("{\"task\": \"")
                        .append(entry.getKey())
                        .append("\", \"done\": ")
                        .append(entry.getValue())
                        .append("}");
            }
            return sb.append("]}").toString();
        }

        private String resolve(String task) {
            if (task == null) {
                return null;
            }
            String needle = task.trim().toLowerCase();
            for (String existing : tasks.keySet()) {
                String hay = existing.trim().toLowerCase();
                if (hay.equals(needle) || hay.contains(needle) || needle.contains(hay)) {
                    return existing;
                }
            }
            return null;
        }
    }

    interface TodoAssistant {
        Result<String> chat(String userMessage);
    }

    private static final List<ToolDefinition> TOOLS = List.of(
            ToolDefinition.of(
                    "addTask",
                    "Add a new task to the user's to-do list",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("task", Map.of("type", "string")),
                            "required", List.of("task"))),
            ToolDefinition.of(
                    "completeTask",
                    "Mark an existing task as done",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("task", Map.of("type", "string")),
                            "required", List.of("task"))),
            ToolDefinition.of(
                    "listTasks",
                    "List all tasks with their done/not-done status",
                    Map.of("type", "object", "properties", Map.of())));

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void simulatorDrivesLiveMultiTurnToolConversationAndEvaluatorsDiscriminate() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_5_MINI)
                .build();

        TodoAssistant assistant = AiServices.builder(TodoAssistant.class)
                .chatModel(chatModel)
                .tools(new TaskStore())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(30))
                .build();

        // Bridge the AiService to a ConversationalApplication; its chat memory carries cross-turn state.
        ConversationalApplication app = trajectory -> {
            Result<String> result = assistant.chat(trajectory.lastUserMessage().content());
            AgentTrace trace = LangChain4jSupport.toAgentTrace(result);
            String response = trace.finalResponse() != null ? trace.finalResponse() : "";
            return Message.assistant(response, trace.toolCalls());
        };

        // Scripted first two user turns guarantee two real assistant turns; the user then drives the
        // (impossible) email request itself.
        SimulatedUser user = LLMSimulatedUser.builder()
                .judge(LangChain4jSupport.asJudge(chatModel))
                .persona("a focused user managing a personal to-do list")
                .behaviorGuidelines("""
                        Have the assistant EMAIL your to-do list to alice@example.com. Ask directly. Do not claim
                        something was done that the assistant did not do. When nothing more can be accomplished,
                        reply exactly: done thanks.""")
                .fixedResponses(List.of(
                        "Add a task called 'buy milk' to my to-do list.", "Now mark the 'buy milk' task as done."))
                .build();

        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(app)
                .scenario("Manage a to-do list")
                .maxTurns(4)
                .stoppingCondition(trajectory -> {
                    Message last = trajectory.lastUserMessage();
                    return last != null && last.content().toLowerCase().contains("done thanks");
                })
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        assertThat(trajectory.metadata())
                .as("no application or simulated-user error")
                .doesNotContainKey("error");

        List<List<ToolCall>> byTurn = trajectory.toolCallsByTurn();
        assertThat(byTurn).as("two scripted turns produced assistant replies").hasSizeGreaterThanOrEqualTo(2);
        assertThat(toolNames(byTurn.get(0))).as("turn 1 adds the task").contains("addTask");
        assertThat(toolNames(byTurn.get(1))).as("turn 2 marks it done").contains("completeTask");

        var error = ToolErrorEvaluator.builder().build();
        var efficiency = ToolEfficiencyEvaluator.builder().build();
        for (int turn = 0; turn < byTurn.size(); turn++) {
            EvalTestCase turnCase = EvalTestCase.builder()
                    .actualOutput("toolCalls", byTurn.get(turn))
                    .metadata("tools", TOOLS)
                    .build();

            assertThat(error.evaluate(turnCase).score())
                    .as("turn %d: every tool call succeeded", turn + 1)
                    .isEqualTo(1.0);
            assertThat(efficiency.evaluate(turnCase).score())
                    .as("turn %d: tool use is not a degenerate loop", turn + 1)
                    .isGreaterThanOrEqualTo(0.5);
        }

        JudgeLM judge = LangChain4jSupport.asJudge(chatModel);
        var taskCompletion = TaskCompletionEvaluator.builder().judge(judge).build();
        List<String> tasks = List.of(
                "Add a task called 'buy milk'",
                "Mark the 'buy milk' task as done",
                "Email the full to-do list to alice@example.com");

        EvalResult completion = taskCompletion.evaluate(trajectory.toTestCase(TOOLS, tasks));
        assertThat(completion.score())
                .as("the impossible email task keeps completion below 1.0. reason=%s", completion.reason())
                .isLessThan(1.0);
    }

    private static List<String> toolNames(List<ToolCall> calls) {
        return calls.stream().map(ToolCall::name).toList();
    }
}
