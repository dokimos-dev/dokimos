package dev.dokimos.server.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests exercising {@link OpenResponsesJudge} against the real OpenAI Responses endpoint,
 * which implements the Open Responses shape. Requires {@code OPENAI_API_KEY}; excluded from
 * {@code mvn test} and run via {@code mvn verify -Dgroups=integration}.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenResponsesJudgeIT {

    private static final String BASE_URL = "https://api.openai.com/v1";
    private static final String MODEL = "gpt-4o-mini";

    @Test
    void generate_shouldReturnModelResponse() {
        OpenResponsesJudge judge = new OpenResponsesJudge(BASE_URL, MODEL, System.getenv("OPENAI_API_KEY"));

        String response = judge.generate("Reply with exactly the word PONG and nothing else.");

        assertThat(response).isNotBlank();
        assertThat(response.toUpperCase()).contains("PONG");
    }

    @Test
    void generate_shouldRaiseNonRetryableErrorOnBadKeyWithoutLeakingIt() {
        String badKey = "sk-this-key-is-invalid-0123456789";
        OpenResponsesJudge judge = new OpenResponsesJudge(BASE_URL, MODEL, badKey);

        assertThatThrownBy(() -> judge.generate("ping")).isInstanceOfSatisfying(JudgeCallException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(401);
            assertThat(e.isRetryable()).isFalse();
            assertThat(e.getMessage()).doesNotContain(badKey);
        });
    }
}
