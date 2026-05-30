package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmResponseUtilsTest {

    @Test
    void shouldStripJsonCodeBlock() {
        String input = """
                ```json
                {"key": "value"}
                ```""";

        String result = LlmResponseUtils.stripMarkdown(input);

        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void shouldStripCodeBlockWithoutLanguage() {
        String input = """
                ```
                {"key": "value"}
                ```""";

        String result = LlmResponseUtils.stripMarkdown(input);

        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void shouldHandleContentWithoutCodeBlock() {
        String input = "{\"key\": \"value\"}";

        String result = LlmResponseUtils.stripMarkdown(input);

        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void shouldTrimWhitespace() {
        String input = "   {\"key\": \"value\"}   ";

        String result = LlmResponseUtils.stripMarkdown(input);

        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void shouldHandleNull() {
        assertThat(LlmResponseUtils.stripMarkdown(null)).isNull();
    }

    @Test
    void shouldHandleEmptyString() {
        assertThat(LlmResponseUtils.stripMarkdown("")).isEmpty();
    }

    @Test
    void shouldHandleCodeBlockWithExtraWhitespace() {
        String input = "```json\n\n{\"key\": \"value\"}\n\n```";

        String result = LlmResponseUtils.stripMarkdown(input);

        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extractJson_dropsPreambleAndTrailingProse() {
        String response =
                "Sure, here is the result:\n{\"score\": 0.8, \"reason\": \"good\"}\nLet me know if you need more.";
        assertThat(LlmResponseUtils.extractJson(response)).isEqualTo("{\"score\": 0.8, \"reason\": \"good\"}");
    }

    @Test
    void extractJson_picksTheArrayWhenItComesFirst() {
        assertThat(LlmResponseUtils.extractJson("```json\n[{\"grounded\": true}]\n```"))
                .isEqualTo("[{\"grounded\": true}]");
    }

    @Test
    void extractJson_returnsStrippedResponseWhenNoJsonPresent() {
        assertThat(LlmResponseUtils.extractJson("no json here")).isEqualTo("no json here");
    }

    @Test
    void lenientMapper_toleratesTrailingCommasAndSingleQuotes() throws Exception {
        Map<String, Object> parsed = LlmResponseUtils.lenientMapper()
                .readValue("{'score': 0.5, 'reason': 'ok',}", new TypeReference<Map<String, Object>>() {});
        assertThat(parsed).containsEntry("score", 0.5).containsEntry("reason", "ok");
    }

    @Test
    void lenientMapper_toleratesUnquotedFieldNames() throws Exception {
        List<Map<String, Object>> parsed = LlmResponseUtils.lenientMapper()
                .readValue("[{grounded: true}, {grounded: false}]", new TypeReference<List<Map<String, Object>>>() {});
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0)).containsEntry("grounded", true);
        assertThat(parsed.get(1)).containsEntry("grounded", false);
    }
}
