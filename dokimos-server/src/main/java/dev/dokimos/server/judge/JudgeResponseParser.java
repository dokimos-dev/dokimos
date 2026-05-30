package dev.dokimos.server.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.LlmResponseUtils;

/**
 * Parses a judge response into a score and reason using the same markdown-stripping and JSON pipeline
 * as the core LLM judge evaluator. Unlike the evaluator, parsing never throws: a malformed or
 * incomplete response yields a {@link ParsedScore#failure} so the worker can record a failing eval
 * result and move on without aborting the job.
 */
final class JudgeResponseParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JudgeResponseParser() {}

    /** Result of parsing a judge response: a numeric score and the judge's reasoning. */
    record ParsedScore(double score, String reason, boolean parsed) {

        static ParsedScore of(double score, String reason) {
            return new ParsedScore(score, reason, true);
        }

        static ParsedScore failure(String reason) {
            return new ParsedScore(0.0, reason, false);
        }
    }

    static ParsedScore parse(String response) {
        try {
            String json = LlmResponseUtils.stripMarkdown(response);
            JsonNode node = OBJECT_MAPPER.readTree(json);
            JsonNode scoreNode = node.get("score");
            if (scoreNode == null || !scoreNode.isNumber()) {
                return ParsedScore.failure("Judge response missing numeric score: " + response);
            }
            double score = scoreNode.asDouble();
            String reason = node.has("reason") ? node.get("reason").asText() : "No reason provided.";
            return ParsedScore.of(score, reason);
        } catch (Exception e) {
            return ParsedScore.failure("Failed to parse judge response: " + e.getMessage());
        }
    }
}
