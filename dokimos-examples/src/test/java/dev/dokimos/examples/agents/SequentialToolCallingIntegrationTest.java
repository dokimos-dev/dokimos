package dev.dokimos.examples.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * End-to-end integration test for a sequential (output -> input -> output) tool-calling workflow
 * against a real OpenAI model.
 *
 * <p>The model must call {@code lookupOrder} first, receive a structured {@link Order} back, then
 * feed that order's {@code weightKg} into {@code estimateShipping} to get a {@link ShippingQuote}.
 * The weight only exists inside tool 1's result, so a correct {@code estimateShipping} weight
 * argument proves the model chained the first tool's output into the second tool's input.
 *
 * <p>The point being proven on the Dokimos side: each executed tool result is attached with {@link
 * ToolCall.Builder#resultJson(Object)} (a structured record, not a {@code toString()}), and {@link
 * ToolCall#resultAs(Class)} round-trips it back into a typed object from a <i>real</i> agent trace.
 *
 * <p>Model: {@link ChatModel#GPT_4O_MINI}. Chosen because it is a current, cheap, tool-calling model
 * that honors {@code temperature(0)} (GPT-5 family models reject a non-default temperature), which
 * keeps the live run deterministic enough for the assertions below.
 *
 * <p>Integration-tagged: excluded from {@code mvn test}, runs under {@code mvn verify
 * -Dgroups=integration}, and requires {@code OPENAI_API_KEY}.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class SequentialToolCallingIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Structured result of {@code lookupOrder}. */
    record Order(String id, String item, double weightKg) {}

    /** Structured result of {@code estimateShipping}. */
    record ShippingQuote(double amountUsd, int days) {}

    /** Typed view of the arguments the model passed to {@code estimateShipping}. */
    record ShippingArgs(double weightKg, String destination) {}

    /** In-memory order book. The weight lives here and nowhere in the prompt. */
    private static final Map<String, Order> ORDER_BOOK = Map.of("A123", new Order("A123", "Trail Runner shoes", 2.5));

    private static Order lookupOrder(String orderId) {
        return ORDER_BOOK.get(orderId);
    }

    private static ShippingQuote estimateShipping(double weightKg, String destination) {
        // Deterministic local computation; destination is accepted but does not vary the math here.
        return new ShippingQuote(5.0 + weightKg * 2.0, 3);
    }

    private static final ToolDefinition LOOKUP_ORDER_TOOL = ToolDefinition.builder()
            .name("lookupOrder")
            .description("Look up an order by its id. Returns the order including its item and weight in kg.")
            .inputSchema(Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of("orderId", Map.of("type", "string", "description", "The order id, e.g. A123")),
                    "required",
                    List.of("orderId")))
            .build();

    private static final ToolDefinition ESTIMATE_SHIPPING_TOOL = ToolDefinition.builder()
            .name("estimateShipping")
            .description(
                    "Estimate the shipping cost and number of days for a package of a given weight to a destination.")
            .inputSchema(Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                            "weightKg",
                            Map.of("type", "number", "description", "Package weight in kilograms"),
                            "destination",
                            Map.of("type", "string", "description", "Destination city")),
                    "required",
                    List.of("weightKg", "destination")))
            .build();

    private static final List<ToolDefinition> TOOLS = List.of(LOOKUP_ORDER_TOOL, ESTIMATE_SHIPPING_TOOL);

    @Test
    void chainsStructuredToolResultsFromOneToolIntoTheNext() {
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        String prompt = "How much will it cost to ship order A123 to Berlin, and how many days?";

        AgentTrace trace = runToolCallingLoop(client, prompt);

        // --- Tool names and ordering: lookupOrder before estimateShipping. ---
        List<String> toolNames = new ArrayList<>(trace.toolNames());
        assertThat(toolNames).contains("lookupOrder", "estimateShipping");
        assertThat(toolNames.indexOf("lookupOrder"))
                .as("lookupOrder must run before estimateShipping")
                .isLessThan(toolNames.indexOf("estimateShipping"));

        ToolCall lookupCall = firstCallNamed(trace, "lookupOrder");
        ToolCall shippingCall = firstCallNamed(trace, "estimateShipping");

        // --- resultAs round-trips the structured Order attached via resultJson. ---
        Order order = lookupCall.resultAs(Order.class);
        assertThat(order).isEqualTo(new Order("A123", "Trail Runner shoes", 2.5));

        // --- resultAs round-trips the structured ShippingQuote. ---
        ShippingQuote quote = shippingCall.resultAs(ShippingQuote.class);
        assertThat(quote).isNotNull();
        assertThat(quote.amountUsd()).isPositive();
        assertThat(quote.days()).isPositive();

        // --- output -> input chaining: the weight passed to estimateShipping came from tool 1. ---
        double passedWeight = ((Number) shippingCall.arguments().get("weightKg")).doubleValue();
        assertThat(passedWeight)
                .as("estimateShipping weight must come from the looked-up order (the only place it exists)")
                .isCloseTo(order.weightKg(), within(0.01));

        // --- argumentsAs round-trips the model's raw tool arguments into a typed record. ---
        // Same guarantee as resultAs, but on the input side: the arguments map the model produced
        // converts cleanly into a ShippingArgs the test code can use without manual map fishing.
        ShippingArgs shippingArgs = shippingCall.argumentsAs(ShippingArgs.class);
        assertThat(shippingArgs).isNotNull();
        assertThat(shippingArgs.weightKg())
                .as("typed weight argument must match the looked-up order weight")
                .isCloseTo(order.weightKg(), within(0.01));
        assertThat(shippingArgs.destination())
                .as("typed destination argument must be present")
                .isNotBlank();

        // --- The trace passes the agent tool-call validity evaluator. ---
        EvalTestCase testCase = trace.toTestCase(prompt, TOOLS);
        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        assertThat(validity.success()).as(validity.reason()).isTrue();
        assertThat(validity.score()).isEqualTo(1.0);
    }

    /**
     * Runs the real tool-calling loop. For each tool the model invokes, this executes the local
     * implementation, attaches the structured result to a Dokimos {@link ToolCall} via {@code
     * resultJson(...)} (NOT {@code result(...)} — that would store a toString and break {@code
     * resultAs}), and feeds the result's JSON back to the model so it can chain into the next call.
     */
    private static AgentTrace runToolCallingLoop(OpenAIClient client, String prompt) {
        AgentTrace.Builder traceBuilder = AgentTrace.builder();

        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI)
                .temperature(0.0)
                .addUserMessage(prompt);
        for (ToolDefinition def : TOOLS) {
            params.addTool(toOpenAiTool(def));
        }

        for (int round = 0; round < 8; round++) {
            var message = client.chat()
                    .completions()
                    .create(params.build())
                    .choices()
                    .get(0)
                    .message();
            params.addMessage(message);

            var toolCalls = message.toolCalls().orElse(List.of());
            if (toolCalls.isEmpty()) {
                traceBuilder.finalResponse(message.content().orElse(""));
                break;
            }

            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                var function = toolCall.asFunction().function();
                String name = function.name();
                Map<String, Object> args = parseArgs(function.arguments());

                Object structuredResult = executeTool(name, args);
                String resultJson = toJson(structuredResult);

                // Attach the STRUCTURED result via resultJson so ToolCall.resultAs can round-trip it.
                traceBuilder.addToolCall(ToolCall.builder()
                        .name(name)
                        .arguments(args)
                        .resultJson(structuredResult)
                        .build());

                // Feed the JSON back to the model so the next turn can use it (output -> input).
                params.addMessage(ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.asFunction().id())
                        .content(resultJson)
                        .build());
            }
        }

        return traceBuilder.build();
    }

    private static Object executeTool(String name, Map<String, Object> args) {
        return switch (name) {
            case "lookupOrder" -> lookupOrder((String) args.get("orderId"));
            case "estimateShipping" ->
                estimateShipping(((Number) args.get("weightKg")).doubleValue(), (String) args.get("destination"));
            default -> Map.of("error", "unknown tool: " + name);
        };
    }

    private static ToolCall firstCallNamed(AgentTrace trace, String name) {
        return trace.toolCalls().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("trace did not contain a call to " + name));
    }

    private static ChatCompletionTool toOpenAiTool(ToolDefinition def) {
        var paramsBuilder = FunctionParameters.builder();
        for (Map.Entry<String, Object> entry : def.inputSchema().entrySet()) {
            paramsBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
        }
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(def.name())
                        .description(def.description())
                        .parameters(paramsBuilder.build())
                        .build())
                .build());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(String argumentsJson) {
        try {
            return MAPPER.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize tool result", e);
        }
    }
}
