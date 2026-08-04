package dev.dokimos.langchain4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.agents.PlanAdherenceEvaluator;
import dev.dokimos.core.evaluators.agents.PlanQualityEvaluator;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Scores realistic agent plans against a real OpenAI judge to confirm {@link PlanQualityEvaluator}
 * and {@link PlanAdherenceEvaluator} discriminate: a coherent plan whose tool calls follow it scores
 * high, while a contradictory plan (or tool calls that ignore the plan) scores strictly lower.
 * Requires {@code OPENAI_API_KEY}.
 */
@Tag("integration")
class LangChain4jPlanEvaluatorIT {

    private static ChatModel model() {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_5_MINI)
                .build();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void planQualityScoresCoherentPlanAboveIncoherentPlan() {
        JudgeLM judge = LangChain4jSupport.asJudge(model());
        var evaluator = PlanQualityEvaluator.builder().judge(judge).build();

        String task = "Find the cheapest flight from New York to Paris next Friday and book it.";

        AgentTrace coherent = AgentTrace.builder()
                .finalResponse("Booked the cheapest flight.")
                .reasoningSteps(List.of(
                        "Search available flights from New York to Paris for next Friday.",
                        "Compare the results and pick the cheapest option.",
                        "Book the selected flight and confirm the reservation."))
                .build();
        EvalTestCase coherentCase = EvalTestCase.builder()
                .input(task)
                .actualOutputs(coherent.toOutputMap())
                .build();

        AgentTrace incoherent = AgentTrace.builder()
                .finalResponse("Done.")
                .reasoningSteps(List.of(
                        "Water the office plants.",
                        "Reboot the coffee machine.",
                        "Refactor an unrelated logging module."))
                .build();
        EvalTestCase incoherentCase = EvalTestCase.builder()
                .input(task)
                .actualOutputs(incoherent.toOutputMap())
                .build();

        EvalResult coherentResult = evaluator.evaluate(coherentCase);
        EvalResult incoherentResult = evaluator.evaluate(incoherentCase);

        assertThat(coherentResult.score())
                .as("a coherent, goal-directed plan scores high. reason=%s", coherentResult.reason())
                .isGreaterThanOrEqualTo(0.75);
        assertThat(incoherentResult.score())
                .as(
                        "a plan unrelated to the task scores strictly lower. coherent=%s incoherent=%s",
                        coherentResult.reason(), incoherentResult.reason())
                .isLessThan(coherentResult.score());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void planAdherenceScoresFollowedPlanAboveIgnoredPlan() {
        JudgeLM judge = LangChain4jSupport.asJudge(model());
        var evaluator = PlanAdherenceEvaluator.builder().judge(judge).build();

        List<String> plan = List.of(
                "Search for flights from New York to Paris.",
                "Pick the cheapest matching flight.",
                "Book the selected flight.");

        // One tool call per plan step. Leaving the "pick the cheapest" step without a matching call
        // makes adherence a judgement call, and a strict judge reads it as not followed.
        AgentTrace followed = AgentTrace.builder()
                .finalResponse("Booked.")
                .reasoningSteps(plan)
                .toolCalls(List.of(
                        ToolCall.of("search_flights", Map.of("from", "New York", "to", "Paris")),
                        ToolCall.of("select_cheapest_flight", Map.of("flightId", "AF123", "price", "412.00")),
                        ToolCall.of("book_flight", Map.of("flightId", "AF123"))))
                .build();
        EvalTestCase followedCase =
                EvalTestCase.builder().actualOutputs(followed.toOutputMap()).build();

        AgentTrace ignored = AgentTrace.builder()
                .finalResponse("Done.")
                .reasoningSteps(plan)
                .toolCalls(List.of(
                        ToolCall.of("order_pizza", Map.of("topping", "pepperoni")),
                        ToolCall.of("send_email", Map.of("to", "someone@example.com"))))
                .build();
        EvalTestCase ignoredCase =
                EvalTestCase.builder().actualOutputs(ignored.toOutputMap()).build();

        EvalResult followedResult = evaluator.evaluate(followedCase);
        EvalResult ignoredResult = evaluator.evaluate(ignoredCase);

        assertThat(followedResult.score())
                .as("tool calls that follow the plan score high. reason=%s", followedResult.reason())
                .isGreaterThanOrEqualTo(0.75);
        assertThat(ignoredResult.score())
                .as(
                        "tool calls that ignore the plan score strictly lower. followed=%s ignored=%s",
                        followedResult.reason(), ignoredResult.reason())
                .isLessThan(followedResult.score());
    }
}
