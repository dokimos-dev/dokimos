package dev.dokimos.server.judge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.server.judge.JudgeResponseParser.ParsedScore;
import org.junit.jupiter.api.Test;

class JudgeResponseParserTest {

    @Test
    void parsesPlainJson() {
        ParsedScore result = JudgeResponseParser.parse("{\"score\": 0.8, \"reason\": \"good\"}");

        assertThat(result.parsed()).isTrue();
        assertThat(result.score()).isEqualTo(0.8);
        assertThat(result.reason()).isEqualTo("good");
    }

    @Test
    void parsesMarkdownWrappedJson() {
        ParsedScore result = JudgeResponseParser.parse("```json\n{\"score\": 1.0, \"reason\": \"perfect\"}\n```");

        assertThat(result.parsed()).isTrue();
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.reason()).isEqualTo("perfect");
    }

    @Test
    void defaultsReasonWhenMissing() {
        ParsedScore result = JudgeResponseParser.parse("{\"score\": 0.5}");

        assertThat(result.parsed()).isTrue();
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.reason()).isEqualTo("No reason provided.");
    }

    @Test
    void failsWhenScoreMissing() {
        ParsedScore result = JudgeResponseParser.parse("{\"reason\": \"no score here\"}");

        assertThat(result.parsed()).isFalse();
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("missing numeric score");
    }

    @Test
    void failsOnMalformedJson() {
        ParsedScore result = JudgeResponseParser.parse("not json at all");

        assertThat(result.parsed()).isFalse();
        assertThat(result.reason()).contains("Failed to parse judge response");
    }
}
