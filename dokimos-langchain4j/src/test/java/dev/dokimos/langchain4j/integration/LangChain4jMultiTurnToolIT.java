package dev.dokimos.langchain4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.evaluators.agents.ArgMatchMode;
import dev.dokimos.core.evaluators.agents.ArgumentMatcher;
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator;
import dev.dokimos.core.evaluators.agents.ToolArgumentHallucinationEvaluator;
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator;
import dev.dokimos.core.evaluators.agents.ToolErrorEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Drives a real two-turn tool-calling conversation against OpenAI through a LangChain4j
 * {@code AiService} and scores it cross-turn with the multi-turn conversation API: each assistant
 * turn is captured via {@link LangChain4jSupport#toAgentTrace(Result)} into a
 * {@link ConversationTrajectory}, then the deterministic tool evaluators run per assistant turn over
 * {@link ConversationTrajectory#toolCallsByTurn()} and a judge {@code TaskCompletionEvaluator} runs
 * over {@link ConversationTrajectory#toTestCase(List, List)}.
 *
 * <p>The model's decision to call each tool is real. The tools mutate a small in-memory task store,
 * so the second turn ("mark it done, then list everything") genuinely depends on the first turn's
 * state being carried by the shared {@link MessageWindowChatMemory}. Requires {@code OPENAI_API_KEY}.
 */
@Tag("integration")
class LangChain4jMultiTurnToolIT {

    /**
     * An in-memory to-do store the model drives across turns. Tool results are real (they reflect the
     * mutated store), not canned; only the model's decision to call each tool is under test.
     */
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

    // The tools the assistant could reach for, wired into the judge and validity-style evaluators.
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
    void scoresLiveMultiTurnToolConversationCrossTurn() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_5_MINI)
                .build();

        // One shared memory makes this a genuine multi-turn run: turn 2 can only resolve "it" and
        // "everything" because turn 1's exchange is still in context.
        TodoAssistant assistant = AiServices.builder(TodoAssistant.class)
                .chatModel(chatModel)
                .tools(new TaskStore())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .build();

        // Two real user turns. Capture each assistant turn's tool calls as they happen.
        ConversationTrajectory.Builder builder =
                ConversationTrajectory.builder().scenario("Manage a to-do list");

        String turn1 = "Add a task called 'buy milk' to my to-do list.";
        Result<String> r1 = assistant.chat(turn1);
        AgentTrace t1 = LangChain4jSupport.toAgentTrace(r1);
        builder.userMessage(turn1).assistantMessage(t1.finalResponse(), t1.toolCalls());

        String turn2 = "Now mark that task as done, then list everything on my to-do list.";
        Result<String> r2 = assistant.chat(turn2);
        AgentTrace t2 = LangChain4jSupport.toAgentTrace(r2);
        builder.userMessage(turn2).assistantMessage(t2.finalResponse(), t2.toolCalls());

        ConversationTrajectory trajectory = builder.build();

        // Two user turns produced two assistant turns.
        List<List<ToolCall>> byTurn = trajectory.toolCallsByTurn();
        assertThat(byTurn).as("two assistant turns").hasSize(2);

        // Turn 1 must add the task; turn 2 must complete and list it. The model picked the tools.
        assertThat(toolNames(byTurn.get(0))).as("turn 1 adds the task").contains("addTask");
        assertThat(toolNames(byTurn.get(1))).as("turn 2 completes and lists").contains("completeTask", "listTasks");

        var error = ToolErrorEvaluator.builder().build();
        var efficiency = ToolEfficiencyEvaluator.builder().build();
        // Compare on tool names and order only; the model chooses argument phrasing.
        var trajectoryEval = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.IN_ORDER)
                .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                .build();

        List<List<String>> expectedNamesByTurn = List.of(List.of("addTask"), List.of("completeTask", "listTasks"));

        for (int turn = 0; turn < byTurn.size(); turn++) {
            List<ToolCall> calls = byTurn.get(turn);

            EvalTestCase turnCase = EvalTestCase.builder()
                    .actualOutput("toolCalls", calls)
                    .expectedOutput("toolCalls", expectedCalls(expectedNamesByTurn.get(turn)))
                    .metadata("tools", TOOLS)
                    .build();

            // Real tool results, so every call should report success (no blank, no JSON "error").
            EvalResult errorResult = error.evaluate(turnCase);
            assertThat(errorResult.score())
                    .as("turn %d: all tool calls succeeded", turn + 1)
                    .isEqualTo(1.0);

            // Each turn's expected tools appear in order within the actual calls.
            EvalResult trajResult = trajectoryEval.evaluate(turnCase);
            assertThat(trajResult.score())
                    .as("turn %d: expected tools called in order", turn + 1)
                    .isEqualTo(1.0);

            // No turn should repeat an identical call; tolerate a model retry by requiring most calls
            // to be distinct rather than demanding a perfect 1.0.
            EvalResult effResult = efficiency.evaluate(turnCase);
            assertThat(effResult.score())
                    .as("turn %d: tool use is efficient", turn + 1)
                    .isGreaterThanOrEqualTo(0.5);
        }

        JudgeLM judge = LangChain4jSupport.asJudge(chatModel);
        var taskCompletion = TaskCompletionEvaluator.builder().judge(judge).build();

        List<String> tasks = List.of("Add a task called 'buy milk'", "Mark the task as done", "List all tasks");
        EvalResult completion = taskCompletion.evaluate(trajectory.toTestCase(TOOLS, tasks));

        assertThat(completion.score())
                .as("judge sees all three tasks completed across the conversation")
                .isEqualTo(1.0);
    }

    /**
     * Runs a real two-turn conversation where the user dictates the exact text that becomes the tool
     * arguments, then checks that {@link ToolArgumentHallucinationEvaluator} does not raise false
     * positives on those grounded arguments. A fabricated-argument trajectory is scored alongside to
     * show the judge discriminates rather than always answering "grounded".
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void scoresLiveGroundedToolArgumentsWithoutFalsePositives() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_5_MINI)
                .build();

        TodoAssistant assistant = AiServices.builder(TodoAssistant.class)
                .chatModel(chatModel)
                .tools(new TaskStore())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .build();

        // The user supplies the exact task text, so every tool argument is grounded in user input.
        final String dentist = "Call the dentist at 555-0142";

        ConversationTrajectory.Builder builder =
                ConversationTrajectory.builder().scenario("Manage a to-do list");

        String turn1 = "Add a task with this exact text: '" + dentist + "'.";
        Result<String> r1 = assistant.chat(turn1);
        AgentTrace t1 = LangChain4jSupport.toAgentTrace(r1);
        String resp1 = t1.finalResponse() != null ? t1.finalResponse() : "";
        builder.userMessage(turn1).assistantMessage(resp1, t1.toolCalls());

        String turn2 = "Now mark the '" + dentist + "' task as done.";
        Result<String> r2 = assistant.chat(turn2);
        AgentTrace t2 = LangChain4jSupport.toAgentTrace(r2);
        String resp2 = t2.finalResponse() != null ? t2.finalResponse() : "";
        builder.userMessage(turn2).assistantMessage(resp2, t2.toolCalls());

        ConversationTrajectory trajectory = builder.build();

        List<List<ToolCall>> byTurn = trajectory.toolCallsByTurn();
        assertThat(byTurn).as("two assistant turns").hasSize(2);
        assertThat(toolNames(byTurn.get(0))).as("turn 1 adds the task").contains("addTask");
        assertThat(toolNames(byTurn.get(1))).as("turn 2 completes the task").contains("completeTask");

        EvalTestCase groundCase = trajectory.toTestCase(TOOLS, List.of("Add the dentist task", "Mark it done"));

        // The user value is present in the grounding, but tool args are rendered name-only.
        String grounding = groundCase.input();
        assertThat(grounding)
                .as("the user-supplied value stays in the grounding")
                .contains(dentist);
        assertThat(grounding).as("tool args are rendered name-only").doesNotContain("[tool: addTask(");
        assertThat(grounding).as("tool line is rendered name-only").contains("[tool: addTask]");

        JudgeLM judge = LangChain4jSupport.asJudge(chatModel);
        var hallucination =
                ToolArgumentHallucinationEvaluator.builder().judge(judge).build();
        EvalResult grounded = hallucination.evaluate(groundCase);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> verdicts =
                (List<Map<String, Object>>) grounded.metadata().get("verdicts");
        // A judge parse failure scores 0.0 with no verdicts metadata; fail clearly instead of NPE-ing.
        assertThat(verdicts)
                .as("judge returned parseable verdicts. reason=%s", grounded.reason())
                .isNotNull();

        // The verbatim user-dictated addTask argument must not be flagged ungrounded.
        boolean addTaskFalseFlagged = verdicts.stream()
                .anyMatch(v -> "addTask".equals(v.get("toolName")) && Boolean.FALSE.equals(v.get("grounded")));
        assertThat(addTaskFalseFlagged)
                .as("the verbatim user-dictated addTask argument must not be flagged ungrounded. verdicts=%s", verdicts)
                .isFalse();

        // Soft aggregate guard against mass false positives; the per-call check above is primary.
        assertThat(grounded.score())
                .as("no mass false positives on user-grounded arguments. reason=%s", grounded.reason())
                .isGreaterThanOrEqualTo(0.5);

        // A fabricated argument the user never supplied must score below the grounded run.
        ToolCall fabricated = ToolCall.builder()
                .name("completeTask")
                .argument("task", "Pay the electric bill")
                .result("{\"error\": \"no such task: Pay the electric bill\"}")
                .build();
        ConversationTrajectory badTrajectory = ConversationTrajectory.builder()
                .scenario("Manage a to-do list")
                .userMessage("Mark the dentist task as done.")
                .assistantMessage("Done.", List.of(fabricated))
                .build();
        EvalResult bad = hallucination.evaluate(badTrajectory.toTestCase(TOOLS, List.of("Mark a task done")));
        assertThat(bad.score())
                .as(
                        "a fabricated argument scores below the grounded run. bad=%s grounded=%s",
                        bad.reason(), grounded.reason())
                .isLessThan(grounded.score());
    }

    private static List<String> toolNames(List<ToolCall> calls) {
        return calls.stream().map(ToolCall::name).toList();
    }

    private static List<ToolCall> expectedCalls(List<String> names) {
        List<ToolCall> calls = new ArrayList<>();
        for (String name : names) {
            calls.add(ToolCall.of(name, Map.of()));
        }
        return calls;
    }
}
