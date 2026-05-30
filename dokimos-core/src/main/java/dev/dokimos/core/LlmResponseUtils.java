package dev.dokimos.core;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Utility methods for processing LLM responses.
 * <p>
 * Provides common parsing functionality for handling responses from
 * {@link JudgeLM} implementations. Judges are language models, so their replies are not always
 * strict JSON: they may wrap the payload in a markdown fence, add a sentence of preamble, or emit
 * single quotes, unquoted keys, or a trailing comma. The helpers here recover the JSON payload and
 * parse it leniently so an otherwise correct judgment is not lost to a formatting quirk.
 */
public final class LlmResponseUtils {

    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    private LlmResponseUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * An {@link ObjectMapper} configured to tolerate the common ways a language model deviates from
     * strict JSON (trailing commas, single quotes, unquoted field names, comments, and unescaped
     * control characters). Shared and thread-safe for reads.
     *
     * @return the shared lenient object mapper
     */
    public static ObjectMapper lenientMapper() {
        return LENIENT_MAPPER;
    }

    /**
     * Strips markdown code block formatting from an LLM response.
     * <p>
     * Many LLMs wrap JSON responses in markdown code blocks like:
     * <pre>
     * ```json
     * {"key": "value"}
     * ```
     * </pre>
     * This method removes such formatting to extract the raw content.
     *
     * @param response the LLM response that may contain markdown formatting
     * @return the response with markdown code block formatting removed
     */
    public static String stripMarkdown(String response) {
        if (response == null) {
            return null;
        }
        return response.strip()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "")
                .strip();
    }

    /**
     * Extracts the JSON payload from an LLM response. Markdown fences are removed, then the substring
     * from the first opening brace or bracket to its matching closing character is returned, dropping
     * any preamble or trailing prose around the JSON. When the response contains both an object and an
     * array, the one that appears first is used. When no JSON structure is found the stripped response
     * is returned unchanged so the caller surfaces a meaningful parse error.
     *
     * @param response the LLM response that may wrap JSON in prose or a code fence
     * @return the extracted JSON text, or null when the response is null
     */
    public static String extractJson(String response) {
        if (response == null) {
            return null;
        }
        String stripped = stripMarkdown(response);
        int objectStart = stripped.indexOf('{');
        int arrayStart = stripped.indexOf('[');

        int start;
        char close;
        boolean arrayFirst = arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart);
        if (arrayFirst) {
            start = arrayStart;
            close = ']';
        } else if (objectStart >= 0) {
            start = objectStart;
            close = '}';
        } else {
            return stripped;
        }

        int end = stripped.lastIndexOf(close);
        if (end > start) {
            return stripped.substring(start, end + 1);
        }
        return stripped;
    }
}
