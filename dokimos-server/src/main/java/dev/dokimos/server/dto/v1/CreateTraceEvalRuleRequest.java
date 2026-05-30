package dev.dokimos.server.dto.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.dokimos.server.entity.TraceMatchType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to create or replace a trace eval rule. {@code matchKey} is required only when
 * {@code matchType} is {@link TraceMatchType#ATTRIBUTE}; for {@link TraceMatchType#SPAN_NAME} it is
 * ignored. {@code minScore} must be less than {@code maxScore}.
 */
public record CreateTraceEvalRuleRequest(
        @NotBlank String name,
        Boolean enabled,
        @NotNull TraceMatchType matchType,
        String matchKey,
        @NotBlank String matchValue,
        @NotNull UUID connectionId,
        @NotBlank String evaluatorName,
        @NotBlank String criteria,
        double minScore,
        double maxScore,
        Double threshold) {

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }

    @AssertTrue(message = "matchKey is required when matchType is ATTRIBUTE")
    @JsonIgnore
    public boolean isMatchKeyValid() {
        return matchType != TraceMatchType.ATTRIBUTE || (matchKey != null && !matchKey.isBlank());
    }

    @AssertTrue(message = "minScore must be less than maxScore")
    @JsonIgnore
    public boolean isScoreRangeValid() {
        return minScore < maxScore;
    }
}
