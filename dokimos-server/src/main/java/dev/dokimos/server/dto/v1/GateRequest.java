package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to evaluate a CI regression gate for an already-ingested candidate run.
 *
 * @param candidateRunId the terminal run to gate; required
 * @param baselineRunId  an explicit baseline run to compare against; when null the baseline is
 *                       resolved automatically (most recent terminal run of the same experiment)
 * @param baselineBranch when set (and {@code baselineRunId} is null), restricts automatic baseline
 *                       resolution to runs on this git branch
 */
public record GateRequest(
        @NotNull(message = "candidateRunId is required") UUID candidateRunId,
        UUID baselineRunId,
        String baselineBranch) {}
