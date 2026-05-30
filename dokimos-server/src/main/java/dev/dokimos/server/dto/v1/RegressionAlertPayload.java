package dev.dokimos.server.dto.v1;

import java.util.UUID;

/**
 * JSON body POSTed to a project's alert webhooks when a completed run regresses against its baseline.
 * Carries enough context to identify the regression without a follow-up API call.
 *
 * @param projectName the project the run belongs to
 * @param experimentId the experiment the run belongs to
 * @param experimentName the experiment name
 * @param runId the candidate run that regressed
 * @param baselineRunId the baseline run it was compared against
 * @param baselinePassRate the baseline run's pass rate
 * @param candidatePassRate the candidate run's pass rate
 * @param passRateDelta candidate minus baseline pass rate (negative on a regression)
 * @param regressedCaseCount the number of items that regressed
 */
public record RegressionAlertPayload(
        String projectName,
        UUID experimentId,
        String experimentName,
        UUID runId,
        UUID baselineRunId,
        double baselinePassRate,
        double candidatePassRate,
        double passRateDelta,
        int regressedCaseCount) {}
