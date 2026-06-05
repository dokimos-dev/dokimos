package dev.dokimos.embabel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.embabel.agent.api.event.AgentProcessCompletedEvent;
import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.AgentProcessFailedEvent;
import com.embabel.agent.api.event.ToolCallRequestEvent;
import com.embabel.agent.api.event.ToolCallResponseEvent;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.OutputType;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for {@link EmbabelTraceCollector}. Embabel's event classes are final Kotlin classes
 * with internal constructors, so they are mocked via the inline mock-maker. The name-mangled
 * {@code getResult-d1pmJ48()} accessor is stubbed with a custom {@link Answer} that returns a real
 * boxed {@code kotlin.Result} built from Java.
 */
class EmbabelTraceCollectorTest {

    @Test
    void happyPathCapturesToolCallsInOrder() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        collector.onProcessEvent(responseEvent(
                "searchHotels", "{\"area\":\"EU\",\"nights\":3}", successResult("found 5 hotels"), 12L, "book"));
        collector.onProcessEvent(responseEvent("bookHotel", "{\"id\":42}", successResult("booked"), 8L, "book"));

        List<ToolCall> calls = collector.toolCalls();
        assertThat(calls).hasSize(2);

        ToolCall first = calls.get(0);
        assertThat(first.name()).isEqualTo("searchHotels");
        assertThat(first.arguments()).containsEntry("area", "EU").containsEntry("nights", 3);
        assertThat(first.result()).isEqualTo("found 5 hotels");
        assertThat(first.metadata()).containsEntry("runningTimeMillis", 12L);

        ToolCall second = calls.get(1);
        assertThat(second.name()).isEqualTo("bookHotel");
        assertThat(second.arguments()).containsEntry("id", 42);
        assertThat(second.result()).isEqualTo("booked");
    }

    @Test
    void structuredArgumentsAndJsonResultReadBackTyped() {
        // Embabel hands tool arguments over as a JSON string and the tool result as a String. The
        // collector parses the arguments into a Map and stores the result verbatim, so the typed-read
        // API added by the structured-output work works on a captured trace: argumentsAs(...) over the
        // parsed Map and resultAs(...) over the JSON result string.
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(responseEvent(
                "searchHotels",
                "{\"area\":\"EU\",\"nights\":3}",
                successResult("{\"id\":\"H1\",\"name\":\"Grand\",\"price\":120}"),
                4L,
                "search"));

        ToolCall call = collector.toolCalls().get(0);

        SearchArgs args = call.argumentsAs(SearchArgs.class);
        assertThat(args).isEqualTo(new SearchArgs("EU", 3));

        // 120 in the JSON result parses structurally to the record's double field (120.0).
        Hotel hotel = call.resultAs(Hotel.class);
        assertThat(hotel).isEqualTo(new Hotel("H1", "Grand", 120.0));
    }

    @Test
    void jsonArrayResultReadsBackAsTypedListViaOutputType() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(
                responseEvent("listHotels", "{}", successResult("[{\"id\":\"H1\"},{\"id\":\"H2\"}]"), 2L, "search"));

        ToolCall call = collector.toolCalls().get(0);
        List<HotelRef> hotels = call.resultAs(new OutputType<List<HotelRef>>() {});
        assertThat(hotels).containsExactly(new HotelRef("H1"), new HotelRef("H2"));
    }

    @Test
    void failureResultSetsErrorMetadataAndNoThrow() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        assertThatCode(() -> collector.onProcessEvent(responseEvent(
                        "searchHotels", "{}", failureResult(new IllegalStateException("upstream down")), 5L, "search")))
                .doesNotThrowAnyException();

        ToolCall call = collector.toolCalls().get(0);
        assertThat(call.result()).isNull();
        assertThat(call.metadata()).containsEntry("error", "upstream down");
    }

    @Test
    void blankAndMalformedInputYieldEmptyArguments() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        collector.onProcessEvent(responseEvent("a", "   ", successResult("ok"), 1L, "x"));
        collector.onProcessEvent(responseEvent("b", "not json", successResult("ok"), 1L, "x"));
        collector.onProcessEvent(responseEvent("c", null, successResult("ok"), 1L, "x"));

        assertThat(collector.toolCalls()).hasSize(3);
        assertThat(collector.toolCalls())
                .allSatisfy(c -> assertThat(c.arguments()).isEmpty());
    }

    @Test
    void completionEventSetsFinalResponse() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        AgentProcessCompletedEvent completed = mock(AgentProcessCompletedEvent.class);
        when(completed.getResult()).thenReturn("final answer");
        collector.onProcessEvent(completed);

        assertThat(collector.trace().finalResponse()).isEqualTo("final answer");
    }

    @Test
    void noEventsYieldsEmptyTraceAndNoThrow() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        AgentTrace trace = collector.trace();
        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isNull();
    }

    @Test
    void requestWithNoResponseIsSilentlyDropped() {
        // A ToolCallRequestEvent without a matching response event maps to no ToolCall: only
        // response events are recorded. This is the documented silent-drop contract.
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        ToolCallRequestEvent request = mock(ToolCallRequestEvent.class);
        lenient().when(request.getTool()).thenReturn("searchHotels");
        AgentProcessEvent requestAsProcessEvent = mock(AgentProcessEvent.class);

        collector.onProcessEvent(requestAsProcessEvent);

        assertThat(collector.toolCalls()).isEmpty();
    }

    @Test
    void unrelatedEventsAreIgnored() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        AgentProcessFailedEvent failed = mock(AgentProcessFailedEvent.class);
        AgentProcessEvent plain = mock(AgentProcessEvent.class);

        assertThatCode(() -> {
                    collector.onProcessEvent(failed);
                    collector.onProcessEvent(plain);
                })
                .doesNotThrowAnyException();

        assertThat(collector.toolCalls()).isEmpty();
        assertThat(collector.trace().finalResponse()).isNull();
    }

    @Test
    void toToolDefinitionsSynthesizesEmptySchemaDefinitions() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(responseEvent("searchHotels", "{}", successResult("ok"), 1L, "x"));
        collector.onProcessEvent(responseEvent("bookHotel", "{}", successResult("ok"), 1L, "x"));
        collector.onProcessEvent(responseEvent("searchHotels", "{}", successResult("ok"), 1L, "x"));

        List<ToolDefinition> defs = EmbabelSupport.toToolDefinitions(collector);
        assertThat(defs).hasSize(2);
        assertThat(defs).extracting(ToolDefinition::name).containsExactly("searchHotels", "bookHotel");
        assertThat(defs).allSatisfy(d -> {
            assertThat(d.name()).isNotBlank();
            assertThat(d.inputSchema()).isEmpty();
        });
    }

    @Test
    void roundTripThroughRealEvaluators() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();
        collector.onProcessEvent(
                responseEvent("searchHotels", "{\"area\":\"EU\"}", successResult("found"), 3L, "search"));
        collector.onProcessEvent(responseEvent("bookHotel", "{\"id\":42}", successResult("booked"), 2L, "book"));

        AgentTrace trace = collector.trace();
        List<ToolDefinition> tools = EmbabelSupport.toToolDefinitions(collector);

        EvalTestCase validityCase = trace.toTestCase("book me a hotel", tools);
        assertThatCode(() -> ToolCallValidityEvaluator.builder().build().evaluate(validityCase))
                .doesNotThrowAnyException();

        // The trajectory evaluator needs an expected trajectory in expectedOutputs; supply one that
        // matches the captured calls and assert a perfect score.
        EvalTestCase trajectoryCase = EvalTestCase.builder()
                .input("book me a hotel")
                .actualOutputs(trace.toOutputMap())
                .expectedOutput("toolCalls", trace.toolCalls())
                .build();
        assertThat(ToolTrajectoryEvaluator.builder()
                        .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                        .build()
                        .evaluate(trajectoryCase)
                        .score())
                .isEqualTo(1.0);
    }

    @Test
    void reusedCollectorDoesNotLeakAcrossRunsAfterReset() {
        EmbabelTraceCollector collector = new EmbabelTraceCollector();

        collector.onProcessEvent(responseEvent("searchHotels", "{}", successResult("a"), 1L, "x"));
        AgentProcessCompletedEvent firstDone = mock(AgentProcessCompletedEvent.class);
        when(firstDone.getResult()).thenReturn("run one");
        collector.onProcessEvent(firstDone);
        assertThat(collector.toolCalls()).hasSize(1);
        assertThat(collector.trace().finalResponse()).isEqualTo("run one");

        collector.reset();
        assertThat(collector.toolCalls()).isEmpty();
        assertThat(collector.observedToolNames()).isEmpty();
        assertThat(collector.trace().finalResponse()).isNull();

        collector.onProcessEvent(responseEvent("bookHotel", "{}", successResult("b"), 1L, "x"));
        assertThat(collector.toolCalls()).hasSize(1);
        assertThat(collector.toolCalls().get(0).name()).isEqualTo("bookHotel");
        assertThat(collector.observedToolNames()).containsExactly("bookHotel");
    }

    // --- helpers ---------------------------------------------------------------------------------

    /**
     * Builds a mocked {@link ToolCallResponseEvent}. The name-mangled {@code getResult-d1pmJ48()}
     * accessor cannot be stubbed with {@code when(...)} (it is not a valid Java identifier), so a
     * custom default {@link Answer} returns the supplied boxed {@code kotlin.Result} for that method
     * and delegates the rest to Mockito's normal stubbing.
     */
    private static ToolCallResponseEvent responseEvent(
            String toolName, String toolInput, Object boxedResult, long runningTimeMillis, String actionName) {
        ToolCallRequestEvent request = mock(ToolCallRequestEvent.class);
        lenient().when(request.getTool()).thenReturn(toolName);
        lenient().when(request.getToolInput()).thenReturn(toolInput);
        lenient().when(request.getCorrelationId()).thenReturn("corr-" + toolName);
        when(request.getAction()).thenReturn(null);

        Answer<Object> mangledResultAnswer = invocation -> {
            Method method = invocation.getMethod();
            if (method.getName().startsWith("getResult-")) {
                return boxedResult;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        };
        ToolCallResponseEvent response = mock(ToolCallResponseEvent.class, mangledResultAnswer);
        lenient().when(response.getRequest()).thenReturn(request);
        lenient().when(response.getRunningTime()).thenReturn(Duration.ofMillis(runningTimeMillis));
        return response;
    }

    /** Builds a real boxed {@code kotlin.Result} success carrying {@code value}, via reflection. */
    private static Object successResult(Object value) {
        try {
            Method box = Class.forName("kotlin.Result").getMethod("box-impl", Object.class);
            return box.invoke(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Builds a real boxed {@code kotlin.Result} failure wrapping {@code throwable}, via reflection. */
    private static Object failureResult(Throwable throwable) {
        try {
            Object failurePayload = Class.forName("kotlin.ResultKt")
                    .getMethod("createFailure", Throwable.class)
                    .invoke(null, throwable);
            Method box = Class.forName("kotlin.Result").getMethod("box-impl", Object.class);
            return box.invoke(null, failurePayload);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- typed-read fixtures ---------------------------------------------------------------------

    record SearchArgs(String area, int nights) {}

    record Hotel(String id, String name, double price) {}

    record HotelRef(String id) {}
}
