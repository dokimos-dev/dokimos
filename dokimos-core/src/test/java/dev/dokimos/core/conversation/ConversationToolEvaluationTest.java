package dev.dokimos.core.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator;
import dev.dokimos.core.evaluators.agents.ToolArgumentHallucinationEvaluator;
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator;
import dev.dokimos.core.evaluators.agents.ToolErrorEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * End-to-end checks that a multi-turn conversation carrying tool calls feeds the deterministic agent
 * evaluators correctly, both collapsed via {@link ConversationTrajectory#toAgentOutputs()} and split
 * per assistant turn via {@link ConversationTrajectory#toolCallsByTurn()}. Also pins coercion parity
 * (typed vs map tool-call lists) and the judge-path dialog shape produced by
 * {@link ConversationTrajectory#toTestCase(java.util.List, java.util.List)}.
 *
 * <p>Everything here is deterministic: the judge is a capturing lambda, no API key required.
 */
class ConversationToolEvaluationTest {

    // A two-turn flight-booking conversation. Turn 1's search errors out (JSON error field); turn 2
    // retries the same search (now succeeding with a structured JSON result) and books a flight.
    private static final ToolCall SEARCH_ERRORED = ToolCall.builder()
            .name("search_flights")
            .argument("dest", "Paris")
            .result("{\"error\":\"no results\"}")
            .build();

    private static final ToolCall SEARCH_OK = ToolCall.builder()
            .name("search_flights")
            .argument("dest", "Paris")
            .resultJson(Map.of("flights", List.of(Map.of("id", "AF123", "price", 240))))
            .build();

    private static final ToolCall BOOK = ToolCall.builder()
            .name("book_flight")
            .argument("id", "AF123")
            .result("confirmed: AF123")
            .build();

    private static ConversationTrajectory trajectory() {
        return ConversationTrajectory.builder()
                .scenario("Book a flight to Paris")
                .userMessage("Find me a flight to Paris")
                .assistantMessage("No flights came back, let me retry.", List.of(SEARCH_ERRORED))
                .userMessage("Please try again and book the cheapest")
                .assistantMessage("Found AF123 at $240, booked it.", List.of(SEARCH_OK, BOOK))
                .build();
    }

    @Nested
    @DisplayName("collapsed over toAgentOutputs()")
    class Collapsed {

        @Test
        @DisplayName("trajectory evaluator scores the flattened call sequence by LCS")
        void trajectoryGradedByLcs() {
            Map<String, Object> outputs = trajectory().toAgentOutputs();

            // Actual flat sequence: [search_flights, search_flights, book_flight] (3 calls).
            @SuppressWarnings("unchecked")
            List<ToolCall> flat = (List<ToolCall>) outputs.get("toolCalls");
            assertThat(flat).hasSize(3);

            var testCase = EvalTestCase.builder()
                    .actualOutputs(outputs)
                    .expectedOutput(
                            "toolCalls",
                            List.of(
                                    ToolCall.of("search_flights", Map.of("dest", "Paris")),
                                    ToolCall.of("book_flight", Map.of("id", "AF123"))))
                    .build();

            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.IN_ORDER)
                    .build()
                    .evaluate(testCase);

            // Expected [search_flights, book_flight] is an ordered subsequence of the 3 actual calls.
            // LCS=2, maxLen=max(3,2)=3 -> 2/3.
            assertThat(result.score()).isEqualTo(2.0 / 3.0);
        }

        @Test
        @DisplayName("error evaluator counts the one errored result over three calls")
        void errorScoreOverWholeRun() {
            var result = ToolErrorEvaluator.builder()
                    .build()
                    .evaluate(EvalTestCase.builder()
                            .actualOutputs(trajectory().toAgentOutputs())
                            .build());

            // Only SEARCH_ERRORED has a top-level "error" field; the other two succeed -> 2/3.
            assertThat(result.score()).isEqualTo(2.0 / 3.0);
            assertThat(result.reason()).contains("2/3");
        }

        @Test
        @DisplayName("efficiency evaluator flags the repeated search as redundant")
        void efficiencyScoreOverWholeRun() {
            var result = ToolEfficiencyEvaluator.builder()
                    .build()
                    .evaluate(EvalTestCase.builder()
                            .actualOutputs(trajectory().toAgentOutputs())
                            .build());

            // The two search_flights calls share name+args (efficiency ignores results), so the
            // second is redundant: distinct=2 of total=3 -> 2/3, with one consecutive duplicate.
            assertThat(result.score()).isEqualTo(2.0 / 3.0);
            assertThat(result.metadata().get("redundantCalls")).isEqualTo(List.of("search_flights"));
            assertThat(result.metadata().get("consecutiveDuplicates")).isEqualTo(List.of("search_flights"));
        }
    }

    @Nested
    @DisplayName("per-turn over toolCallsByTurn()")
    class PerTurn {

        @Test
        @DisplayName("two assistant turns are grouped, error isolated to turn one")
        void errorPerTurn() {
            List<List<ToolCall>> byTurn = trajectory().toolCallsByTurn();
            assertThat(byTurn).hasSize(2);
            assertThat(byTurn.get(0)).containsExactly(SEARCH_ERRORED);
            assertThat(byTurn.get(1)).containsExactly(SEARCH_OK, BOOK);

            var error = ToolErrorEvaluator.builder().build();

            // Turn 1: the lone call errored -> 0/1.
            assertThat(error.evaluate(turnCase(byTurn.get(0))).score()).isEqualTo(0.0);
            // Turn 2: both calls succeeded -> 2/2.
            assertThat(error.evaluate(turnCase(byTurn.get(1))).score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("each turn is internally efficient even though the run repeats a call")
        void efficiencyPerTurn() {
            List<List<ToolCall>> byTurn = trajectory().toolCallsByTurn();
            var efficiency = ToolEfficiencyEvaluator.builder().build();

            // No turn repeats a call within itself, so each scores 1.0 even though the collapsed
            // run is only 2/3 efficient.
            assertThat(efficiency.evaluate(turnCase(byTurn.get(0))).score()).isEqualTo(1.0);
            assertThat(efficiency.evaluate(turnCase(byTurn.get(1))).score()).isEqualTo(1.0);
        }

        private static EvalTestCase turnCase(List<ToolCall> calls) {
            return EvalTestCase.builder().actualOutput("toolCalls", calls).build();
        }
    }

    @Nested
    @DisplayName("coercion parity")
    class CoercionParity {

        @Test
        @DisplayName("a map-shaped tool-call list scores identically to the typed list")
        void mapListMatchesTypedList() {
            // The typed actual sequence the trajectory produces.
            List<ToolCall> typed = trajectory().toolCalls();

            // The same calls as a dataset would deserialize them: a List<Map>.
            List<Map<String, Object>> asMaps = List.of(
                    Map.of("name", "search_flights", "arguments", Map.of("dest", "Paris")),
                    Map.of("name", "search_flights", "arguments", Map.of("dest", "Paris")),
                    Map.of("name", "book_flight", "arguments", Map.of("id", "AF123")));

            var evaluator = ToolEfficiencyEvaluator.builder().build();

            EvalResult fromTyped = evaluator.evaluate(
                    EvalTestCase.builder().actualOutput("toolCalls", typed).build());
            EvalResult fromMaps = evaluator.evaluate(
                    EvalTestCase.builder().actualOutput("toolCalls", asMaps).build());

            // Coercion of the map list must land on the exact same score and redundancy diagnosis.
            assertThat(fromMaps.score()).isEqualTo(fromTyped.score()).isEqualTo(2.0 / 3.0);
            assertThat(fromMaps.metadata().get("redundantCalls"))
                    .isEqualTo(fromTyped.metadata().get("redundantCalls"));
        }
    }

    @Nested
    @DisplayName("judge-path dialog shape")
    class JudgePathShape {

        @Test
        @DisplayName("task completion sees the full transcript once, with no doubled labels or duplicated turn")
        void judgeSeesTranscriptOnce() {
            var trajectory = trajectory();
            var captured = new AtomicReference<String>();

            // Stub judge: capture the prompt, report every task done so the score is deterministic.
            JudgeLM judge = prompt -> {
                captured.set(prompt);
                return "{\"tasks\": [{\"task\": \"Book a flight to Paris\", \"completed\": true, \"reason\": \"AF123 booked\"}]}";
            };

            var evaluator = TaskCompletionEvaluator.builder().judge(judge).build();
            EvalTestCase testCase = trajectory.toTestCase(List.of(), List.of("Book a flight to Paris"));
            var result = evaluator.evaluate(testCase);

            assertThat(result.score()).isEqualTo(1.0);

            String prompt = captured.get();
            // The judge sees the grounding transcript (tool calls name-only), not args-inclusive toText().
            String transcript = testCase.input();

            // The transcript appears verbatim inside the prompt exactly once.
            assertThat(prompt).contains(transcript);
            assertThat(countOccurrences(prompt, transcript)).isEqualTo(1);

            // The transcript already carries its own role labels (USER:/ASSISTANT:), so the judge must
            // not wrap it again under a second "User:/Agent:" layer.
            assertThat(prompt).doesNotContain("Agent: " + transcript);
            assertThat(prompt).doesNotContain("User: " + transcript);

            // The final assistant turn appears once in the prompt: the dialog is not duplicated as a
            // separate "Agent:" output the way resolveDialog would for a plain input+output test case.
            String finalTurn = trajectory.lastAssistantMessage().content();
            assertThat(countOccurrences(prompt, finalTurn)).isEqualTo(1);
        }

        @Test
        @DisplayName("toTestCase(tools, tasks) puts the grounding transcript in input and leaves no separate output")
        void noSeparateOutput() {
            var trajectory = trajectory();
            EvalTestCase testCase = trajectory.toTestCase(List.of(), List.of("Book a flight to Paris"));

            // Tool calls are rendered name-only, never the args-inclusive form that toText() emits.
            assertThat(testCase.input()).contains("[tool: search_flights]").contains("[tool: book_flight]");
            assertThat(testCase.input()).doesNotContain("[tool: search_flights(");
            assertThat(testCase.input()).isNotEqualTo(trajectory.toText());
            // No "output" key -> resolveDialog returns the input alone instead of "User: .. Agent: ..".
            assertThat(testCase.actualOutput()).isNull();
        }

        private static int countOccurrences(String haystack, String needle) {
            int count = 0;
            int from = 0;
            int idx;
            while ((idx = haystack.indexOf(needle, from)) >= 0) {
                count++;
                from = idx + needle.length();
            }
            return count;
        }
    }

    @Nested
    @DisplayName("hallucination judge grounding")
    class HallucinationGrounding {

        @Test
        @DisplayName("the fabricated argument under test never leaks into the grounding transcript")
        void argumentUnderTestIsAbsentFromGrounding() {
            // The user asks to book a flight but never names a flight id; the assistant fabricates one.
            ToolCall fabricated = ToolCall.builder()
                    .name("book_flight")
                    .argument("id", "ZZ999")
                    .result("confirmed: ZZ999")
                    .build();

            var trajectory = ConversationTrajectory.builder()
                    .scenario("Book a flight to Paris")
                    .userMessage("Book me a flight to Paris")
                    .assistantMessage("Booked it.", List.of(fabricated))
                    .build();

            var captured = new AtomicReference<String>();
            JudgeLM judge = prompt -> {
                captured.set(prompt);
                return "[{\"toolName\": \"book_flight\", \"grounded\": false, \"reason\": \"id not in input\"}]";
            };

            var evaluator =
                    ToolArgumentHallucinationEvaluator.builder().judge(judge).build();
            evaluator.evaluate(trajectory.toTestCase(List.of(), List.of("Book a flight to Paris")));

            String prompt = captured.get();
            String groundingInput = trajectory.toTestCase(List.of(), List.of()).input();

            // The grounding the judge reads must not contain the fabricated argument value.
            assertThat(groundingInput).doesNotContain("ZZ999");
            // The judge still receives it via the separate tool-calls section, so it can flag it.
            assertThat(prompt).contains("ZZ999");
        }
    }
}
