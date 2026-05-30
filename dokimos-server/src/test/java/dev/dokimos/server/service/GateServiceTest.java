package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.server.dto.v1.GateRequest;
import dev.dokimos.server.dto.v1.GateResult;
import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.entity.DatasetItem;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.entity.EvalResult;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Service-level coverage for {@link GateService} backed by H2 with the real {@link
 * dev.dokimos.core.comparison.RunComparison} engine (not mocked), so the significance gate is
 * exercised end to end.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(GateServiceTest.TestConfig.class)
class GateServiceTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        ComparisonSupport comparisonSupport(ItemResultRepository itemResultRepository) {
            return new ComparisonSupport(itemResultRepository);
        }

        @Bean
        GateService gateService(
                ExperimentRepository experimentRepository,
                ExperimentRunRepository runRepository,
                ComparisonSupport comparisonSupport) {
            return new GateService(experimentRepository, runRepository, comparisonSupport);
        }
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GateService gateService;

    @Test
    void consistentRegression_failsGate_withLinkedDatasetItems() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(8);

        // Baseline: every item passes with score 1.0. Candidate: every item fails with 0.0.
        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, 1.0, true);
            fixture.itemResult(candidate, item, 0.0, false);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.passed()).isFalse();
        assertThat(result.pairing()).isEqualTo("dataset_item_id");
        assertThat(result.regressedCount()).isPositive();
        assertThat(result.cases()).isNotEmpty();
        assertThat(result.cases().get(0).datasetItemId()).isNotNull();
        assertThat(result.regressedEvaluators()).isNotEmpty();
        assertThat(result.baselineRunId()).isEqualTo(baseline.getId());
    }

    @Test
    void identicalRuns_passGate() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(8);

        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, 0.9, true);
            fixture.itemResult(candidate, item, 0.9, true);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.passed()).isTrue();
        assertThat(result.regressedCount()).isZero();
        assertThat(result.cases()).isEmpty();
    }

    @Test
    void noisyWobble_aboveEpsilon_butNotConsistentRegression_passesGate() {
        Fixture fixture = new Fixture();
        // Eight items whose per-item deltas all exceed epsilon (0.001) but wobble in both directions
        // with no consistent regression. The significance gate must hold and the gate must PASS.
        DatasetVersion version = fixture.datasetVersion(8);

        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        double[] baseScores = {0.80, 0.85, 0.78, 0.83, 0.81, 0.86, 0.79, 0.84};
        // Deltas: +0.05, -0.06, +0.04, -0.07, +0.08, -0.05, +0.06, -0.04. Balanced wobble, no trend.
        double[] candScores = {0.85, 0.79, 0.82, 0.76, 0.89, 0.81, 0.85, 0.80};
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, baseScores[i], true);
            fixture.itemResult(candidate, item, candScores[i], true);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.passed()).isTrue();
        assertThat(result.regressedCount()).isZero();
    }

    @Test
    void datasetLinkedRun_withUnlinkedItems_fallsBackToPositionalPairing() {
        Fixture fixture = new Fixture();
        // Both runs reference the same dataset version, but some item results are unlinked (null
        // datasetItem), as the T9d "store unlinked on stale id" path can produce. The id-pairing guard
        // must detect this, avoid the colliding positional fallback key inside the engine, and pair
        // positionally instead. The gate must produce a verdict without throwing.
        DatasetVersion version = fixture.datasetVersion(4);

        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            // Link only the first two items on each side; the rest are unlinked (stale id path).
            DatasetItem item = i < 2 ? fixture.item(version, i) : null;
            fixture.itemResult(baseline, item, 0.9, true);
            fixture.itemResult(candidate, item, 0.9, true);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.pairing()).isEqualTo("positional");
        assertThat(result.status()).isIn("PASS", "FAIL");
        assertThat(result.baselineRunId()).isEqualTo(baseline.getId());
    }

    @Test
    void cancelledCandidate_raisesIllegalState() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(2);
        ExperimentRun candidate = fixture.run(version, RunStatus.CANCELLED, "main");
        entityManager.flush();
        entityManager.clear();

        UUID experimentId = fixture.experimentId();
        UUID candidateId = candidate.getId();
        assertThatThrownBy(() -> gateService.evaluateGate(
                        experimentId,
                        new GateRequest(candidateId, null, null),
                        dev.dokimos.server.tenant.TenantScope.unrestricted()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal SUCCESS/FAILED")
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void onlyPriorRunFailed_autoResolutionReturnsNoBaseline_butExplicitFailedBaselineHonored() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(4);

        // The only prior terminal run is FAILED. Automatic resolution must exclude it (NO_BASELINE),
        // but an explicit baselineRunId pointing at the same FAILED run is accepted (it is terminal).
        ExperimentRun failedPrior = fixture.run(version, RunStatus.FAILED, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(failedPrior, item, 1.0, true);
            fixture.itemResult(candidate, item, 1.0, true);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult auto = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());
        assertThat(auto.status()).isEqualTo("NO_BASELINE");
        assertThat(auto.baselineRunId()).isNull();

        GateResult explicit = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), failedPrior.getId(), null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());
        assertThat(explicit.baselineRunId()).isEqualTo(failedPrior.getId());
        assertThat(explicit.status()).isIn("PASS", "FAIL");
    }

    @Test
    void manyRegressedCases_areTruncatedAt50_withAuthoritativeCount() {
        Fixture fixture = new Fixture();
        int itemCount = 60;
        DatasetVersion version = fixture.datasetVersion(itemCount);

        // Every item regresses hard (1.0 -> 0.0), yielding 60 regressed cases, above the cap of 50.
        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < itemCount; i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, 1.0, true);
            fixture.itemResult(candidate, item, 0.0, false);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.cases()).hasSize(50);
        assertThat(result.regressedCount()).isEqualTo(itemCount);
        assertThat(result.casesTruncated()).isTrue();
    }

    @Test
    void noBaselineResolvable_returnsNoBaseline_passed() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(4);

        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            fixture.itemResult(candidate, fixture.item(version, i), 1.0, true);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.status()).isEqualTo("NO_BASELINE");
        assertThat(result.passed()).isTrue();
        assertThat(result.baselineRunId()).isNull();
        assertThat(result.pairing()).isEqualTo("none");
    }

    @Test
    void explicitBaselineRunId_isHonored() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(6);

        // A more recent terminal run would win automatic resolution; the explicit id must override.
        ExperimentRun olderBaseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun decoyNewer = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(olderBaseline, item, 1.0, true);
            fixture.itemResult(decoyNewer, item, 0.0, false);
            fixture.itemResult(candidate, item, 1.0, true);
        }
        // Make decoy strictly newer than the candidate is irrelevant; what matters is the override.
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), olderBaseline.getId(), null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.baselineRunId()).isEqualTo(olderBaseline.getId());
        assertThat(result.status()).isEqualTo("PASS");
    }

    @Test
    void baselineBranchFilter_selectsRightBaseline() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(6);

        // Newest run is on a feature branch; filtering to "main" must skip it and pick the main run.
        ExperimentRun mainBaseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun featureRun = fixture.run(version, RunStatus.SUCCESS, "feature/x");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "feature/x");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(mainBaseline, item, 1.0, true);
            fixture.itemResult(featureRun, item, 1.0, true);
            fixture.itemResult(candidate, item, 1.0, true);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, "main"),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.baselineRunId()).isEqualTo(mainBaseline.getId());
    }

    @Test
    void candidateStillRunning_raisesIllegalState() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(2);
        ExperimentRun candidate = fixture.run(version, RunStatus.RUNNING, "main");
        entityManager.flush();
        entityManager.clear();

        UUID experimentId = fixture.experimentId();
        UUID candidateId = candidate.getId();
        assertThatThrownBy(() -> gateService.evaluateGate(
                        experimentId,
                        new GateRequest(candidateId, null, null),
                        dev.dokimos.server.tenant.TenantScope.unrestricted()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal SUCCESS/FAILED")
                .hasMessageContaining("RUNNING");
    }

    @Test
    void adHocRuns_noDatasetVersion_pairPositionally_producesVerdict() {
        Fixture fixture = new Fixture();

        ExperimentRun baseline = fixture.run(null, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(null, RunStatus.SUCCESS, "main");
        double[] baseScores = {1.0, 1.0, 1.0, 1.0};
        for (int i = 0; i < baseScores.length; i++) {
            fixture.itemResult(baseline, null, baseScores[i], true);
            fixture.itemResult(candidate, null, baseScores[i], true);
        }
        entityManager.flush();
        entityManager.clear();

        GateResult result = gateService.evaluateGate(
                fixture.experimentId(),
                new GateRequest(candidate.getId(), null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(result.pairing()).isEqualTo("positional");
        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.baselineRunId()).isEqualTo(baseline.getId());
    }

    @Test
    void runFromAnotherExperiment_raisesNotFound() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(2);
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");

        Experiment otherExperiment = entityManager.persist(new Experiment(fixture.project, "other-exp"));
        entityManager.flush();
        entityManager.clear();

        UUID otherId = otherExperiment.getId();
        UUID candidateId = candidate.getId();
        assertThatThrownBy(() -> gateService.evaluateGate(
                        otherId,
                        new GateRequest(candidateId, null, null),
                        dev.dokimos.server.tenant.TenantScope.unrestricted()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to experiment");
    }

    /** Builds a project, experiment, dataset, and runs directly through the entity manager. */
    private final class Fixture {
        private final Project project;
        private final Experiment experiment;
        private final Dataset dataset;
        private int runOrdinal;

        Fixture() {
            this.project = entityManager.persist(new Project("proj-" + UUID.randomUUID()));
            this.experiment = entityManager.persist(new Experiment(project, "exp"));
            this.dataset = entityManager.persist(new Dataset("ds-" + UUID.randomUUID(), null));
        }

        UUID experimentId() {
            return experiment.getId();
        }

        DatasetVersion datasetVersion(int itemCount) {
            return entityManager.persist(new DatasetVersion(dataset, 1, null, "tester", itemCount));
        }

        DatasetItem item(DatasetVersion version, int ordinal) {
            return entityManager.persist(
                    new DatasetItem(version, ordinal, Map.of("q", "q" + ordinal), Map.of("a", "a" + ordinal), null));
        }

        ExperimentRun run(DatasetVersion version, RunStatus status, String branch) {
            ExperimentRun run = new ExperimentRun(experiment, Map.of());
            setField(run, "status", status);
            // Stagger startedAt so "most recent" ordering is deterministic regardless of clock.
            setField(run, "startedAt", Instant.now().plusSeconds(runOrdinal++));
            run.setGitBranch(branch);
            if (version != null) {
                run.setDatasetVersion(version);
            }
            return entityManager.persist(run);
        }

        void itemResult(ExperimentRun run, DatasetItem datasetItem, double score, boolean success) {
            ItemResult item =
                    new ItemResult(run, Map.of("q", "q"), Map.of("a", "a"), Map.of("a", success ? "a" : "wrong"), null);
            if (datasetItem != null) {
                item.setDatasetItem(datasetItem);
            }
            EvalResult eval = new EvalResult("accuracy", score, 0.5, success, "reason");
            item.addEvalResult(eval);
            entityManager.persist(item);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
