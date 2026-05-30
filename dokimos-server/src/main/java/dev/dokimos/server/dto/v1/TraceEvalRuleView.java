package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.entity.TraceMatchType;
import java.time.Instant;
import java.util.UUID;

/** Public view of a {@link TraceEvalRule}. */
public record TraceEvalRuleView(
        UUID id,
        UUID projectId,
        String name,
        boolean enabled,
        TraceMatchType matchType,
        String matchKey,
        String matchValue,
        UUID connectionId,
        String evaluatorName,
        String criteria,
        double minScore,
        double maxScore,
        Double threshold,
        Instant createdAt,
        Instant updatedAt) {

    public static TraceEvalRuleView from(TraceEvalRule rule) {
        return new TraceEvalRuleView(
                rule.getId(),
                rule.getProjectId(),
                rule.getName(),
                rule.isEnabled(),
                rule.getMatchType(),
                rule.getMatchKey(),
                rule.getMatchValue(),
                rule.getConnection().getId(),
                rule.getEvaluatorName(),
                rule.getCriteria(),
                rule.getMinScore(),
                rule.getMaxScore(),
                rule.getThreshold(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
