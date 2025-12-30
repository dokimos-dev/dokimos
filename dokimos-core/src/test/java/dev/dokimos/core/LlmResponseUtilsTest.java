package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
