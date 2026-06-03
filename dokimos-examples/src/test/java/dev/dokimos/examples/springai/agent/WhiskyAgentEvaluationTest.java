package dev.dokimos.examples.springai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.OutputType;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.StructuralMatchEvaluator;
import dev.dokimos.core.evaluators.StructuralMatchMode;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * Deterministic, network-free evaluation of the whisky agent. Runs in CI with no API key.
 *
 * <p>Demonstrates two things:
 *
 * <ul>
 *   <li>Spring AI messages become a Dokimos {@link AgentTrace} in one
 *       {@code SpringAiSupport.toAgentTrace(...)} call.
 *   <li>Tool results and final outputs stay structured ({@code List<Whisky>}, {@code Whisky})
 *       through {@link ToolCall.Builder#resultJson(Object)} and the typed output accessors — no
 *       escaped JSON strings.
 * </ul>
 */
class WhiskyAgentEvaluationTest {

    private static final WhiskyCatalog CATALOG = new WhiskyCatalog();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize test fixture", e);
        }
    }

    private static final ToolDefinition SEARCH_TOOL = ToolDefinition.builder()
            .name("searchWhiskies")
            .description("Search the whisky catalog by name, region, or flavor terms.")
            .inputSchema(Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of("query", Map.of("type", "string")),
                    "required",
                    List.of("query")))
            .build();

    @Test
    void mapsSpringAiMessagesToTraceInOneCall() {
        // Build the Spring AI messages by hand (no client, no network) and let the support class do
        // the AssistantMessage -> AgentTrace mapping.
        List<Whisky> islay12 = CATALOG.search("Islay 12");
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("I'd suggest Ardbeg An Oa.")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "searchWhiskies", "{\"query\":\"peaty Islay 12\"}")))
                .build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "searchWhiskies", json(islay12))))
                .build();

        AgentTrace trace = dev.dokimos.springai.SpringAiSupport.toAgentTrace(assistantMessage, List.of(toolResponse));

        assertThat(trace.toolNames()).containsExactly("searchWhiskies");
        assertThat(trace.toolCalls().get(0).arguments()).containsEntry("query", "peaty Islay 12");
        assertThat(trace.finalResponse()).isEqualTo("I'd suggest Ardbeg An Oa.");
    }

    @Test
    void validTraceWithStructuredToolResultPassesAgentEvaluators() {
        AgentTrace trace = peatyIslayTrace();
        EvalTestCase testCase =
                trace.toTestCase("Find me a peaty Islay whisky around 12 years old", List.of(SEARCH_TOOL));

        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        assertThat(validity.score()).isEqualTo(1.0);
        assertThat(validity.success()).isTrue();

        EvalResult correctness = ToolCorrectnessEvaluator.builder()
                .build()
                .evaluate(withExpectedToolCalls(testCase, ToolCall.of("searchWhiskies", Map.of())));
        assertThat(correctness.score()).isEqualTo(1.0);
        assertThat(correctness.success()).isTrue();
    }

    @Test
    void unknownToolFailsValidity() {
        AgentTrace trace = AgentTrace.builder()
                .finalResponse("done")
                .addToolCall(ToolCall.builder()
                        .name("lookupBottle")
                        .argument("query", "Islay")
                        .build())
                .build();
        EvalTestCase testCase = trace.toTestCase("Find a whisky", List.of(SEARCH_TOOL));

        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        assertThat(validity.score()).isEqualTo(0.0);
        assertThat(validity.success()).isFalse();
        assertThat(validity.reason()).contains("0/1");
    }

    @Test
    void wrongArgumentFailsCorrectnessWithArgsMode() {
        // The agent searched "Speyside" when the expected call searched "Islay".
        AgentTrace trace = AgentTrace.builder()
                .finalResponse("Try Glenfiddich 12.")
                .addToolCall(ToolCall.builder()
                        .name("searchWhiskies")
                        .argument("query", "Speyside")
                        .resultJson(CATALOG.search("Speyside"))
                        .build())
                .build();
        EvalTestCase testCase = withExpectedToolCalls(
                trace.toTestCase("Find a peaty Islay whisky", List.of(SEARCH_TOOL)),
                ToolCall.builder()
                        .name("searchWhiskies")
                        .argument("query", "Islay")
                        .build());

        EvalResult correctness = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .build()
                .evaluate(testCase);
        assertThat(correctness.score()).isEqualTo(0.0);
        assertThat(correctness.success()).isFalse();
    }

    @Test
    void structuredFinalOutputComparesStructurally() {
        Whisky recommended = new Whisky("ard12", "Ardbeg An Oa", "Islay", 12, true);
        Whisky expected = new Whisky("ard12", "Ardbeg An Oa", "Islay", 12, true);

        // Structured objects in/out — no stringified JSON to assemble or escape.
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutput("output", recommended)
                .expectedOutput("output", expected)
                .build();

        EvalResult strict = StructuralMatchEvaluator.builder()
                .mode(StructuralMatchMode.STRICT)
                .build()
                .evaluate(testCase);
        assertThat(strict.score()).isEqualTo(1.0);
        assertThat(strict.success()).isTrue();

        // Read it back typed, straight off the test case.
        Whisky readBack = testCase.expectedOutputAs(Whisky.class);
        assertThat(readBack).isEqualTo(expected);
        assertThat(testCase.<Whisky>actualOutputAs(Whisky.class).region()).isEqualTo("Islay");
    }

    @Test
    void lenientStructuralMatchIgnoresExtraActualFields() {
        // Actual carries an extra "score" field the expected contract does not mention.
        Map<String, Object> actual = Map.of("id", "ard12", "name", "Ardbeg An Oa", "region", "Islay", "score", 0.92);
        Map<String, Object> expected = Map.of("id", "ard12", "name", "Ardbeg An Oa", "region", "Islay");

        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutput("output", actual)
                .expectedOutput("output", expected)
                .build();

        EvalResult lenient = StructuralMatchEvaluator.builder()
                .mode(StructuralMatchMode.LENIENT)
                .build()
                .evaluate(testCase);
        assertThat(lenient.score()).isEqualTo(1.0);

        EvalResult strict = StructuralMatchEvaluator.builder()
                .mode(StructuralMatchMode.STRICT)
                .build()
                .evaluate(testCase);
        assertThat(strict.score()).isLessThan(1.0);
    }

    @Test
    void typedListReadBackFromStructuredToolResult() {
        AgentTrace trace = peatyIslayTrace();
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutput("matches", CATALOG.search("Islay"))
                .build();

        List<Whisky> matches = testCase.actualOutputAs("matches", new OutputType<List<Whisky>>() {});
        assertThat(matches).isNotEmpty();
        assertThat(matches).allSatisfy(w -> assertThat(w.region()).isEqualTo("Islay"));
        // The structured tool result on the trace is the same data, attached via resultJson(...).
        assertThat(trace.toolCalls().get(0).result()).contains("Ardbeg An Oa");
    }

    /** A valid trace whose tool result is a structured {@code List<Whisky>} attached via resultJson. */
    private static AgentTrace peatyIslayTrace() {
        return AgentTrace.builder()
                .finalResponse("I'd suggest Ardbeg An Oa, a peaty 12-year-old Islay.")
                .addToolCall(ToolCall.builder()
                        .name("searchWhiskies")
                        .argument("query", "peaty Islay 12")
                        .resultJson(CATALOG.search("Islay"))
                        .build())
                .build();
    }

    private static EvalTestCase withExpectedToolCalls(EvalTestCase base, ToolCall... expected) {
        return EvalTestCase.builder()
                .inputs(base.inputs())
                .actualOutputs(base.actualOutputs())
                .metadata(base.metadata())
                .expectedOutput("toolCalls", List.of(expected))
                .build();
    }
}
