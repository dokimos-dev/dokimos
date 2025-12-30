package dev.dokimos.core;

/**
 * Utility methods for processing LLM responses.
 * <p>
 * Provides common parsing functionality for handling responses from
 * {@link JudgeLM} implementations.
 */
public final class LlmResponseUtils {

    private LlmResponseUtils() {
        // Utility class, prevent instantiation
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
}
