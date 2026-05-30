package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.AnnotationVerdict;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A run item surfaced in the review queue because it still needs a human verdict, carrying enough run,
 * experiment, and project context for a reviewer to act on it without opening the run first.
 *
 * @param itemId           the item result id, used to write an annotation back
 * @param runId            the run the item belongs to
 * @param experimentId     the experiment the run belongs to
 * @param experimentName   the experiment name
 * @param projectName      the project name
 * @param input            the item input
 * @param expectedOutput   the expected output, if any
 * @param actualOutput     the produced output
 * @param evalResults      the automated eval results for the item
 * @param currentVerdict   the existing verdict when the item was previously marked {@code UNSURE}, else
 *     null for a never-annotated item
 * @param createdAt        when the item result was recorded
 */
public record ReviewQueueItem(
        UUID itemId,
        UUID runId,
        UUID experimentId,
        String experimentName,
        String projectName,
        Map<String, Object> input,
        Map<String, Object> expectedOutput,
        Map<String, Object> actualOutput,
        List<RunDetails.EvalSummary> evalResults,
        AnnotationVerdict currentVerdict,
        Instant createdAt) {}
