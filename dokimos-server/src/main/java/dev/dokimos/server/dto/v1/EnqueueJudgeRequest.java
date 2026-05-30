package dev.dokimos.server.dto.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.dokimos.core.EvalTestCaseParam;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request to enqueue a server-side judge job for a run. {@code evaluationParams} names which test-case
 * fields are included in the judge prompt; each entry must match a {@link EvalTestCaseParam} name.
 */
public record EnqueueJudgeRequest(
        @NotNull UUID connectionId,
        @NotBlank String evaluatorName,
        @NotBlank String criteria,
        List<String> evaluationParams,
        double minScore,
        double maxScore,
        Double threshold) {

    public EnqueueJudgeRequest {
        evaluationParams = evaluationParams == null ? List.of() : List.copyOf(evaluationParams);
    }

    @AssertTrue(message = "evaluationParams must be non-empty and contain valid parameter names")
    @JsonIgnore
    public boolean isEvaluationParamsValid() {
        if (evaluationParams.isEmpty()) {
            return false;
        }
        for (String param : evaluationParams) {
            if (!isKnownParam(param)) {
                return false;
            }
        }
        return true;
    }

    @AssertTrue(message = "minScore must be less than maxScore")
    @JsonIgnore
    public boolean isScoreRangeValid() {
        return minScore < maxScore;
    }

    private static boolean isKnownParam(String name) {
        if (name == null) {
            return false;
        }
        for (EvalTestCaseParam param : EvalTestCaseParam.values()) {
            if (param.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
