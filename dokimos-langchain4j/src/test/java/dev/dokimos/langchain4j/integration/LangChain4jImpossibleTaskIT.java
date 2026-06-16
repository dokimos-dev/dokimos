package dev.dokimos.langchain4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
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
 * The negative twin of {@link LangChain4jMultiTurnToolIT}: it proves the evaluators discriminate
 * rather than rubber-stamp a happy path. The user asks for one achievable task and one impossible one
 * (email a reminder, for which no tool exists); {@link ToolCorrectnessEvaluator} and
 * {@link TaskCompletionEvaluator} both score below 1.0. Requires {@code OPENAI_API_KEY}.
 */
@Tag("integration")
class LangChain4jImpossibleTaskIT {

    /** A real to-do store with no email, reminder, or notify tool. */
    static class TaskStore {

        private final Map<String, Boolean> tasks = new LinkedHashMap<>();

        @Tool("Add a new task to the user's to-do list")
        String addTask(@P("the task description") String task) {
            tasks.put(task, false);
            return "{\"added\": \"" + task + "\"}";
        }

        @Tool("Mark an existing task as done")
        String completeTask(@P("the task description to complete") String task) {
            if (!tasks.containsKey(task)) {
                return "{\"error\": \"no such task: " + task + "\"}";
            }
            tasks.put(task, true);
            return "{\"completed\": \"" + task + "\"}";
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
    void evaluatorsDiscriminateWhenAnAskedTaskIsStructurallyImpossible() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_5_MINI)
                .build();

        TodoAssistant assistant = AiServices.builder(TodoAssistant.class)
                .chatModel(chatModel)
                .tools(new TaskStore())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .build();

        ConversationTrajectory.Builder builder =
                ConversationTrajectory.builder().scenario("To-do list with an impossible email ask");

        // Turn 1: the achievable ask alone, so addTask is near-certain before the impossible ask.
        String turn1 = "Add a task called 'buy milk' to my to-do list.";
        Result<String> r1 = assistant.chat(turn1);
        AgentTrace t1 = LangChain4jSupport.toAgentTrace(r1);
        String resp1 = t1.finalResponse() != null ? t1.finalResponse() : "";
        builder.userMessage(turn1).assistantMessage(resp1, t1.toolCalls());

        // Turn 2: impossible. No email/reminder tool exists.
        String turn2 = "Now email me a reminder about that task.";
        Result<String> r2 = assistant.chat(turn2);
        AgentTrace t2 = LangChain4jSupport.toAgentTrace(r2);
        String resp2 = t2.finalResponse() != null ? t2.finalResponse() : "";
        builder.userMessage(turn2).assistantMessage(resp2, t2.toolCalls());

        ConversationTrajectory trajectory = builder.build();
        List<ToolCall> calls = trajectory.toolCalls();

        assertThat(calls.stream().map(ToolCall::name))
                .as("the achievable task used addTask")
                .contains("addTask");

        // Control scoped to addTask so a stray completeTask error cannot flake it.
        List<ToolCall> addCalls =
                calls.stream().filter(c -> "addTask".equals(c.name())).toList();
        EvalTestCase errorCase =
                EvalTestCase.builder().actualOutput("toolCalls", addCalls).build();
        assertThat(ToolErrorEvaluator.builder().build().evaluate(errorCase).score())
                .as("the executed addTask succeeded")
                .isEqualTo(1.0);

        // The expected sendEmailReminder tool is never registered, so recall (and F1) stays below 1.0.
        EvalTestCase correctnessCase = EvalTestCase.builder()
                .actualOutput("toolCalls", calls)
                .expectedOutput(
                        "toolCalls",
                        List.of(ToolCall.of("addTask", Map.of()), ToolCall.of("sendEmailReminder", Map.of())))
                .build();
        EvalResult correctness = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_ONLY)
                .build()
                .evaluate(correctnessCase);
        assertThat(correctness.score())
                .as("the missing email tool is caught. reason=%s", correctness.reason())
                .isLessThan(1.0);

        // The judge sees no email action in the transcript, so it cannot credit the email task.
        JudgeLM judge = LangChain4jSupport.asJudge(chatModel);
        List<String> tasks =
                List.of("Add a task called 'buy milk' to the to-do list", "Email the user a reminder about the task");
        EvalResult completion =
                TaskCompletionEvaluator.builder().judge(judge).build().evaluate(trajectory.toTestCase(TOOLS, tasks));
        assertThat(completion.score())
                .as("the impossible email task is not credited. reason=%s", completion.reason())
                .isLessThan(1.0);
    }
}
