package dev.dokimos.openai.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionTool;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.agents.ArgMatchMode;
import dev.dokimos.core.evaluators.agents.ArgumentMatcher;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import dev.dokimos.openai.OpenAiSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Verifies that {@link OpenAiSupport#toAgentTrace} and {@link OpenAiSupport#toToolCalls} capture
 * real tool calls from a live OpenAI tool-calling run. Requires {@code OPENAI_API_KEY}.
 */
@Tag("integration")
class OpenAiAgentTraceIT {

    private static final ChatCompletionTool WEATHER_TOOL =
            ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                    .function(FunctionDefinition.builder()
                            .name("get_weather")
                            .description("Get the current weather for a city")
                            .parameters(FunctionParameters.builder()
                                    .putAdditionalProperty("type", JsonValue.from("object"))
                                    .putAdditionalProperty(
                                            "properties",
                                            JsonValue.from(Map.of(
                                                    "city", Map.of("type", "string", "description", "City name"))))
                                    .putAdditionalProperty("required", JsonValue.from(List.of("city")))
                                    .build())
                            .build())
                    .build());

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void capturesToolCallsFromLiveAgent() {
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();
        String userMessage = "What is the weather in Paris right now?";

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_5_NANO)
                .addUserMessage(userMessage)
                .addTool(WEATHER_TOOL)
                .build();

        ChatCompletionMessage message =
                client.chat().completions().create(params).choices().get(0).message();

        AgentTrace trace = OpenAiSupport.toAgentTrace(
                message, id -> "{\"city\": \"Paris\", \"temperatureC\": 18, \"conditions\": \"cloudy\"}");

        assertThat(trace.toolNames())
                .as("agent should have called the weather tool")
                .contains("get_weather");
        assertThat(OpenAiSupport.toToolCalls(message)).isNotEmpty().allSatisfy(call -> assertThat(call.name())
                .isNotBlank());

        EvalTestCase testCase = EvalTestCase.builder()
                .input(userMessage)
                .actualOutput("toolCalls", trace.toolCalls())
                .expectedOutput("toolCalls", List.of(ToolCall.of("get_weather", Map.of())))
                .build();

        double score = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                .build()
                .evaluate(testCase)
                .score();
        assertThat(score).isGreaterThanOrEqualTo(0.5);
    }
}
