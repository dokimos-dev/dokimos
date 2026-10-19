package dev.dokimos.adk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GroundingChunk;
import com.google.genai.types.GroundingChunkRetrievedContext;
import com.google.genai.types.GroundingChunkWeb;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.RagChunk;
import dev.dokimos.core.AsyncTask;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.MeasuredTask;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.Task;
import dev.dokimos.core.TaskResult;
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.conversation.ConversationalApplication;
import dev.dokimos.core.conversation.Message;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdkSupportTest {

    private static BaseLlm fakeLlm(String replyText, GenerateContentResponseUsageMetadata usage) {
        return new BaseLlm("gemini-3.8-flash") {
            @Override
            public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
                LlmResponse.Builder builder = LlmResponse.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText(replyText))
                                .build());
                if (usage != null) {
                    builder.usageMetadata(usage);
                }
                return Flowable.just(builder.build());
            }

            @Override
            public BaseLlmConnection connect(LlmRequest llmRequest) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void asJudgeDelegatesToBaseLlmAndRunner() {
        BaseLlm llm = fakeLlm("{\"score\": 1.0}", null);
        JudgeLM judge = AdkSupport.asJudge(llm);
        assertThat(judge.generate("Evaluate this")).isEqualTo("{\"score\": 1.0}");

        LlmAgent agent = LlmAgent.builder()
                .name("judge_agent")
                .model(llm)
                .instruction("Judge carefully")
                .build();
        JudgeLM agentJudge = AdkSupport.asJudge(agent);
        assertThat(agentJudge.generate("Evaluate this")).isEqualTo("{\"score\": 1.0}");
    }

    @Test
    void simpleTaskAndAsyncTaskProduceExpectedOutputs() {
        BaseLlm llm = fakeLlm("Paris", null);

        Task llmTask = AdkSupport.simpleTask(llm);
        assertThat(llmTask.run(Example.of("Capital of France?", "Paris"))).containsEntry("output", "Paris");

        Task customKeyTask = AdkSupport.simpleTask(llm, "answer");
        assertThat(customKeyTask.run(Example.of("Capital of France?", "Paris"))).containsEntry("answer", "Paris");

        AsyncTask asyncLlmTask = AdkSupport.asyncTask(llm);
        assertThat(asyncLlmTask
                        .run(Example.of("Capital of France?", "Paris"))
                        .join()
                        .outputs())
                .containsEntry("output", "Paris");

        LlmAgent agent = LlmAgent.builder()
                .name("qa_agent")
                .model(llm)
                .instruction("Answer concisely")
                .build();

        Task agentSimpleTask = AdkSupport.simpleTask(agent);
        assertThat(agentSimpleTask.run(Example.of("Capital of France?", "Paris")))
                .containsEntry("output", "Paris");

        AsyncTask agentAsyncTask = AdkSupport.asyncTask(agent);
        assertThat(agentAsyncTask
                        .run(Example.of("Capital of France?", "Paris"))
                        .join()
                        .outputs())
                .containsEntry("output", "Paris");

        AsyncTask asyncAgentTraceTask = AdkSupport.asyncAgentTask(agent);
        assertThat(asyncAgentTraceTask
                        .run(Example.of("Capital of France?", "Paris"))
                        .join()
                        .outputs())
                .containsEntry("output", "Paris")
                .containsKey("toolCalls")
                .containsKey("reasoningSteps");
    }

    @Test
    void asyncTaskRunsOnProvidedExecutor() {
        BaseLlm llm = fakeLlm("Executed on pool", null);
        AtomicBoolean usedCustomExecutor = new AtomicBoolean(false);
        var executor = Executors.newSingleThreadExecutor(r -> new Thread(() -> {
            usedCustomExecutor.set(true);
            r.run();
        }));

        try {
            AsyncTask task = AdkSupport.asyncTask(llm, executor);
            TaskResult result = task.run(Example.of("hi", "hello")).join();
            assertThat(result.outputs()).containsEntry("output", "Executed on pool");
            assertThat(usedCustomExecutor.get()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void asyncTaskPropagatesErrorsCleanly() {
        Runner failingRunner = mock(Runner.class);
        when(failingRunner.runAsync(anyString(), anyString(), any(Content.class), any(RunConfig.class)))
                .thenReturn(Flowable.error(new IllegalStateException("ADK runner failure")));

        AsyncTask asyncTask = AdkSupport.asyncTask(failingRunner);
        assertThatThrownBy(() -> asyncTask.run(Example.of("hi", "hello")).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADK runner failure");
    }

    @Test
    void measuredTasksCaptureTokensLatencyAndCost() {
        GenerateContentResponseUsageMetadata usage = GenerateContentResponseUsageMetadata.builder()
                .promptTokenCount(1_000)
                .candidatesTokenCount(500)
                .totalTokenCount(1_500)
                .build();
        PriceTable prices = (model, tokensIn, tokensOut) -> {
            if (!"gemini-3.8-flash".equals(model) || tokensIn == null || tokensOut == null) {
                return null;
            }
            return (tokensIn * 0.15 + tokensOut * 0.60) / 1_000_000.0;
        };

        BaseLlm llm = fakeLlm("Measured output", usage);
        MeasuredTask measuredLlm = AdkSupport.measuredTask(llm, "gemini-3.8-flash", prices);
        TaskResult llmResult = measuredLlm.run(Example.of("prompt", "expected"));

        assertThat(llmResult.outputs()).containsEntry("output", "Measured output");
        CallMetrics metrics = llmResult.metrics();
        assertThat(metrics.tokensIn()).isEqualTo(1_000);
        assertThat(metrics.tokensOut()).isEqualTo(500);
        assertThat(metrics.latencyMs()).isGreaterThanOrEqualTo(0L);
        // 1,000 * 0.15 / 1,000,000 + 500 * 0.60 / 1,000,000 = 0.00015 + 0.00030 = 0.00045
        assertThat(metrics.costUsd()).isCloseTo(0.00045, within(1e-9));

        LlmAgent agent = LlmAgent.builder()
                .name("measured_agent")
                .model(llm)
                .instruction("Be helpful")
                .build();

        MeasuredTask measuredAgent = AdkSupport.measuredAgentTask(agent, "gemini-3.8-flash", prices);
        TaskResult agentResult = measuredAgent.run(Example.of("prompt", "expected"));
        assertThat(agentResult.outputs()).containsEntry("output", "Measured output");
        assertThat(agentResult.metrics().tokensIn()).isEqualTo(1_000);
        assertThat(agentResult.metrics().tokensOut()).isEqualTo(500);

        AsyncTask measuredAsync =
                AdkSupport.measuredAsyncAgentTask(new InMemoryRunner(agent), "gemini-3.8-flash", prices);
        TaskResult asyncResult =
                measuredAsync.run(Example.of("prompt", "expected")).join();
        assertThat(asyncResult.outputs()).containsEntry("output", "Measured output");
        assertThat(asyncResult.metrics().tokensIn()).isEqualTo(1_000);
        assertThat(asyncResult.metrics().tokensOut()).isEqualTo(500);
    }

    @Test
    void toCallMetricsHandlesNullAndZeroUsage() {
        CallMetrics nullMetrics =
                AdkSupport.toCallMetrics((GenerateContentResponseUsageMetadata) null, 42L, "gemini-3.8-flash", null);
        assertThat(nullMetrics.latencyMs()).isEqualTo(42L);
        assertThat(nullMetrics.tokensIn()).isNull();
        assertThat(nullMetrics.tokensOut()).isNull();
        assertThat(nullMetrics.costUsd()).isNull();

        GenerateContentResponseUsageMetadata zeroUsage = GenerateContentResponseUsageMetadata.builder()
                .promptTokenCount(0)
                .candidatesTokenCount(0)
                .build();
        CallMetrics zeroMetrics = AdkSupport.toCallMetrics(zeroUsage, 15L, "gemini-3.8-flash", null);
        assertThat(zeroMetrics.tokensIn()).isNull();
        assertThat(zeroMetrics.tokensOut()).isNull();
    }

    @Test
    void convertsMultiTurnEventsAndSessionIntoConversationTrajectory() {
        Event u1 = Event.builder()
                .id("1")
                .author("user")
                .content(Content.fromParts(Part.fromText("Book a flight to Tokyo")))
                .build();
        Event a1Call = Event.builder()
                .id("2")
                .author("travel_agent")
                .content(Content.fromParts(Part.builder()
                        .functionCall(FunctionCall.builder()
                                .id("c1")
                                .name("search_flights")
                                .args(Map.of("destination", "Tokyo"))
                                .build())
                        .build()))
                .build();
        Event a1Resp = Event.builder()
                .id("3")
                .author("travel_agent")
                .content(Content.fromParts(Part.builder()
                        .functionResponse(FunctionResponse.builder()
                                .id("c1")
                                .name("search_flights")
                                .response(Map.of("flights", List.of("NH101", "JL202")))
                                .build())
                        .build()))
                .build();
        Event a1Reply = Event.builder()
                .id("4")
                .author("travel_agent")
                .content(Content.fromParts(Part.fromText("I found NH101 and JL202. Which do you prefer?")))
                .build();
        Event u2 = Event.builder()
                .id("5")
                .author("user")
                .content(Content.fromParts(Part.fromText("Book NH101 please")))
                .build();
        Event a2Reply = Event.builder()
                .id("6")
                .author("travel_agent")
                .content(Content.fromParts(Part.fromText("NH101 is booked for you!")))
                .build();

        Session session = Session.builder("sess-1")
                .appName("travel")
                .userId("u1")
                .events(List.of(u1, a1Call, a1Resp, a1Reply, u2, a2Reply))
                .build();

        ConversationTrajectory trajectory = AdkSupport.toConversationTrajectory(session, "Flight booking");

        assertThat(trajectory.scenario()).isEqualTo("Flight booking");
        assertThat(trajectory.turnCount()).isEqualTo(2);
        assertThat(trajectory.userMessages())
                .extracting(Message::content)
                .containsExactly("Book a flight to Tokyo", "Book NH101 please");
        assertThat(trajectory.assistantMessages())
                .extracting(Message::content)
                .containsExactly("I found NH101 and JL202. Which do you prefer?", "NH101 is booked for you!");
        assertThat(trajectory.toolCallsByTurn().get(0)).hasSize(1);
        assertThat(trajectory.toolCallsByTurn().get(0).get(0).name()).isEqualTo("search_flights");
        assertThat(trajectory.toolCallsByTurn().get(1)).isEmpty();
    }

    @Test
    void asConversationalAppSupportsMultiTurnConversationSimulation() {
        AtomicInteger turn = new AtomicInteger(0);
        BaseLlm multiTurnLlm = new BaseLlm("fake-gemini") {
            @Override
            public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
                int current = turn.incrementAndGet();
                return Flowable.just(LlmResponse.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText("Reply for turn " + current))
                                .build())
                        .build());
            }

            @Override
            public BaseLlmConnection connect(LlmRequest llmRequest) {
                throw new UnsupportedOperationException();
            }
        };

        LlmAgent agent = LlmAgent.builder()
                .name("dialog_agent")
                .model(multiTurnLlm)
                .instruction("Converse")
                .build();

        ConversationalApplication app = AdkSupport.asConversationalApp(agent);

        ConversationTrajectory t1 =
                ConversationTrajectory.builder().userMessage("Hello").build();
        Message r1 = app.respond(t1);
        assertThat(r1.content()).isEqualTo("Reply for turn 1");

        ConversationTrajectory t2 = t1.withMessage(r1).withMessage(Message.user("Follow-up question"));
        Message r2 = app.respond(t2);
        assertThat(r2.content()).isEqualTo("Reply for turn 2");
    }

    @Test
    void extractTextsReadsGroundingMetadataAndToolResponses() {
        GroundingMetadata grounding = GroundingMetadata.builder()
                .groundingChunks(List.of(
                        GroundingChunk.builder()
                                .retrievedContext(GroundingChunkRetrievedContext.builder()
                                        .text("Refund policy: 30-day money-back guarantee.")
                                        .build())
                                .build(),
                        GroundingChunk.builder()
                                .retrievedContext(GroundingChunkRetrievedContext.builder()
                                        .ragChunk(RagChunk.builder()
                                                .text("Shipping takes 5-7 business days.")
                                                .build())
                                        .build())
                                .build(),
                        GroundingChunk.builder()
                                .web(GroundingChunkWeb.builder()
                                        .title("Dokimos Docs")
                                        .uri("https://dokimos.dev")
                                        .build())
                                .build()))
                .build();

        Event groundedEvent = Event.builder()
                .id("1")
                .author("rag_agent")
                .groundingMetadata(grounding)
                .content(Content.fromParts(Part.fromText("We offer a 30-day refund.")))
                .build();

        assertThat(AdkSupport.extractTexts(List.of(groundedEvent)))
                .containsExactly(
                        "Refund policy: 30-day money-back guarantee.",
                        "Shipping takes 5-7 business days.",
                        "Dokimos Docs (https://dokimos.dev)");

        // Tool-based RAG (e.g., VertexAiRagRetrieval returning Map.of("response", List<String>))
        Event toolResponseEvent = Event.builder()
                .id("2")
                .author("rag_agent")
                .content(Content.fromParts(Part.builder()
                        .functionResponse(FunctionResponse.builder()
                                .name("search_kb")
                                .response(Map.of("response", List.of("Doc chunk A", "Doc chunk B")))
                                .build())
                        .build()))
                .build();
        Event unrelatedToolEvent = Event.builder()
                .id("3")
                .author("rag_agent")
                .content(Content.fromParts(Part.builder()
                        .functionResponse(FunctionResponse.builder()
                                .name("get_time")
                                .response(Map.of("now", "12:00"))
                                .build())
                        .build()))
                .build();

        // Filtered by retrievalToolNames
        assertThat(AdkSupport.extractTexts(List.of(toolResponseEvent, unrelatedToolEvent), "search_kb"))
                .containsExactly("Doc chunk A", "Doc chunk B");

        // Unfiltered: extracts recognized "response" key from search_kb
        assertThat(AdkSupport.extractTexts(List.of(toolResponseEvent))).containsExactly("Doc chunk A", "Doc chunk B");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragTasksPopulateOutputAndContextAndMetrics() {
        GroundingMetadata grounding = GroundingMetadata.builder()
                .groundingChunks(List.of(GroundingChunk.builder()
                        .retrievedContext(GroundingChunkRetrievedContext.builder()
                                .text("Full refunds within 30 days of purchase.")
                                .build())
                        .build()))
                .build();
        GenerateContentResponseUsageMetadata usage = GenerateContentResponseUsageMetadata.builder()
                .promptTokenCount(50)
                .candidatesTokenCount(25)
                .build();

        BaseLlm ragLlm = new BaseLlm("gemini-3.8-flash") {
            @Override
            public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
                return Flowable.just(LlmResponse.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText("You can get a full refund within 30 days."))
                                .build())
                        .groundingMetadata(grounding)
                        .usageMetadata(usage)
                        .build());
            }

            @Override
            public BaseLlmConnection connect(LlmRequest llmRequest) {
                throw new UnsupportedOperationException();
            }
        };

        LlmAgent agent = LlmAgent.builder()
                .name("rag_agent")
                .model(ragLlm)
                .instruction("Answer from context")
                .build();

        // Sync ragTask
        Task ragTask = AdkSupport.ragTask(agent);
        Map<String, Object> syncOut = ragTask.run(Example.of("What is the refund policy?", "30 days"));
        assertThat(syncOut.get("output")).isEqualTo("You can get a full refund within 30 days.");
        assertThat((List<String>) syncOut.get("context")).containsExactly("Full refunds within 30 days of purchase.");

        // Custom keys ragTask
        Task customKeyTask = AdkSupport.ragTask(agent, "question", "answer", "retrievedContext");
        Map<String, Object> customOut = customKeyTask.run(Example.builder()
                .input("question", "What is the refund policy?")
                .build());
        assertThat(customOut.get("answer")).isEqualTo("You can get a full refund within 30 days.");
        assertThat((List<String>) customOut.get("retrievedContext"))
                .containsExactly("Full refunds within 30 days of purchase.");

        // Async ragTask
        AsyncTask asyncRag = AdkSupport.asyncRagTask(agent);
        TaskResult asyncRes = asyncRag.run(Example.of("What is the refund policy?", "30 days"))
                .join();
        assertThat(asyncRes.outputs().get("output")).isEqualTo("You can get a full refund within 30 days.");
        assertThat((List<String>) asyncRes.outputs().get("context"))
                .containsExactly("Full refunds within 30 days of purchase.");

        // Measured ragTask
        PriceTable prices = (m, in, out) -> (in + out) * 0.001;
        MeasuredTask measuredRag = AdkSupport.measuredRagTask(agent, "gemini-3.8-flash", prices);
        TaskResult measuredRes = measuredRag.run(Example.of("What is the refund policy?", "30 days"));
        assertThat(measuredRes.outputs().get("output")).isEqualTo("You can get a full refund within 30 days.");
        assertThat((List<String>) measuredRes.outputs().get("context"))
                .containsExactly("Full refunds within 30 days of purchase.");
        assertThat(measuredRes.metrics().tokensIn()).isEqualTo(50);
        assertThat(measuredRes.metrics().tokensOut()).isEqualTo(25);
        assertThat(measuredRes.metrics().costUsd()).isCloseTo(0.075, within(1e-6));

        // Measured async ragTask
        AsyncTask measuredAsyncRag = AdkSupport.measuredAsyncRagTask(agent, "gemini-3.8-flash", prices);
        TaskResult measuredAsyncRes = measuredAsyncRag
                .run(Example.of("What is the refund policy?", "30 days"))
                .join();
        assertThat(measuredAsyncRes.metrics().tokensIn()).isEqualTo(50);
        assertThat(measuredAsyncRes.metrics().tokensOut()).isEqualTo(25);
    }

    @Test
    void rejectsNullArgumentsOnFactoryMethods() {
        assertThatThrownBy(() -> AdkSupport.asJudge((BaseLlm) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.asJudge((Runner) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.simpleTask((BaseLlm) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.simpleTask((Runner) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.agentTask((Runner) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.ragTask((Runner) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.asyncTask((BaseLlm) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.asyncTask((Runner) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.asyncRagTask((Runner) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.measuredTask((BaseLlm) null, "m", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.measuredTask((Runner) null, "m", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.measuredRagTask((Runner) null, "m", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.attach(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.asConversationalApp((Runner) null))
                .isInstanceOf(NullPointerException.class);
    }
}
