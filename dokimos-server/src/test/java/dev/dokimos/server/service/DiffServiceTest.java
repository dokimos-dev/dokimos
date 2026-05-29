package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.server.dto.v1.DiffCase;
import dev.dokimos.server.dto.v1.DiffView;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Service-level coverage for {@link DiffService} backed by H2 with the real {@link
 * dev.dokimos.core.comparison.RunComparison} engine, exercising case classification, sorting,
 * filtering, pagination, and input mapping end to end.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DiffServiceTest.TestConfig.class)
class DiffServiceTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        ComparisonSupport comparisonSupport(ItemResultRepository itemResultRepository) {
            return new ComparisonSupport(itemResultRepository);
        }

        @Bean
        DiffService diffService(
                ExperimentRepository experimentRepository,
                ExperimentRunRepository runRepository,
                ComparisonSupport comparisonSupport) {
            return new DiffService(experimentRepository, runRepository, comparisonSupport);
        }
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DiffService diffService;

    @Test
    void consistentRegression_sortsRegressedFirst_withPerEvaluatorDrops() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(8);

        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, 1.0, true);
            fixture.itemResult(candidate, item, 0.0, false);
        }
        entityManager.flush();
        entityManager.clear();

        DiffView view = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "ALL", PageRequest.of(0, 50));

        assertThat(view.summary().pairing()).isEqualTo("dataset_item_id");
        assertThat(view.summary().regressedCount()).isPositive();
        assertThat(view.summary().baselineRunId()).isEqualTo(baseline.getId());
        assertThat(view.summary().candidateRunId()).isEqualTo(candidate.getId());

        List<DiffCase> cases = view.cases().content();
        assertThat(cases.get(0).status()).isEqualTo("REGRESSED");
        DiffCase first = cases.get(0);
        assertThat(first.datasetItemId()).isNotNull();
        assertThat(first.evaluators()).isNotEmpty();
        DiffCase.EvaluatorDiff drop = first.evaluators().get(0);
        assertThat(drop.baselineMean()).isEqualTo(1.0);
        assertThat(drop.candidateMean()).isEqualTo(0.0);
        assertThat(drop.delta()).isNegative();
        assertThat(drop.status()).isEqualTo("REGRESSED");
        assertThat(drop.significant()).isTrue();
        assertThat(first.input()).isNotNull();
    }

    @Test
    void identicalRuns_allUnchanged() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(6);

        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, 0.9, true);
            fixture.itemResult(candidate, item, 0.9, true);
        }
        entityManager.flush();
        entityManager.clear();

        DiffView view = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "ALL", PageRequest.of(0, 50));

        assertThat(view.summary().regressedCount()).isZero();
        assertThat(view.summary().improvedCount()).isZero();
        assertThat(view.cases().content())
                .allSatisfy(c -> assertThat(c.status()).isEqualTo("UNCHANGED"));
    }

    @Test
    void statusFilterRegressed_returnsOnlyRegressed() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(8);

        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, 1.0, true);
            fixture.itemResult(candidate, item, 0.0, false);
        }
        entityManager.flush();
        entityManager.clear();

        DiffView view = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "REGRESSED", PageRequest.of(0, 50));

        assertThat(view.cases().content()).isNotEmpty();
        assertThat(view.cases().content())
                .allSatisfy(c -> assertThat(c.status()).isEqualTo("REGRESSED"));
        assertThat(view.cases().totalElements()).isEqualTo(view.summary().regressedCount());
    }

    @Test
    void pagination_sizeTwoOverFiveCases_yieldsThreePages() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(5);

        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, 0.9, true);
            fixture.itemResult(candidate, item, 0.9, true);
        }
        entityManager.flush();
        entityManager.clear();

        DiffView page0 = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "ALL", PageRequest.of(0, 2));

        assertThat(page0.cases().totalElements()).isEqualTo(5);
        assertThat(page0.cases().totalPages()).isEqualTo(3);
        assertThat(page0.cases().content()).hasSize(2);
        assertThat(page0.cases().first()).isTrue();

        DiffView page2 = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "ALL", PageRequest.of(2, 2));
        assertThat(page2.cases().content()).hasSize(1);
        assertThat(page2.cases().last()).isTrue();
    }

    @Test
    void differingItemSets_surfaceAddedAndRemoved() {
        Fixture fixture = new Fixture();
        // Baseline has items 0..3, candidate has items 1..4. Positional pairing aligns by index, so
        // both runs hold four items at indices 0..3; the sets are equal positionally. To force ADDED
        // and REMOVED we use ad-hoc runs (no dataset version) with different item counts, which the
        // engine pairs positionally and flags the trailing extras.
        ExperimentRun baseline = fixture.run(null, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(null, RunStatus.SUCCESS, "main");
        for (int i = 0; i < 4; i++) {
            fixture.itemResult(baseline, null, 0.9, true);
        }
        for (int i = 0; i < 6; i++) {
            fixture.itemResult(candidate, null, 0.9, true);
        }
        entityManager.flush();
        entityManager.clear();

        DiffView view = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "ALL", PageRequest.of(0, 50));

        assertThat(view.summary().pairing()).isEqualTo("positional");
        assertThat(view.summary().addedCount()).isEqualTo(2);
        assertThat(view.cases().content().stream().map(DiffCase::status)).contains("ADDED");
    }

    @Test
    void removedCase_surfacedWhenBaselineHasExtraItems() {
        Fixture fixture = new Fixture();
        ExperimentRun baseline = fixture.run(null, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(null, RunStatus.SUCCESS, "main");
        for (int i = 0; i < 5; i++) {
            fixture.itemResult(baseline, null, 0.9, true);
        }
        for (int i = 0; i < 3; i++) {
            fixture.itemResult(candidate, null, 0.9, true);
        }
        entityManager.flush();
        entityManager.clear();

        DiffView view = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "ALL", PageRequest.of(0, 50));

        assertThat(view.summary().removedCount()).isEqualTo(2);
        DiffCase removed = view.cases().content().stream()
                .filter(c -> c.status().equals("REMOVED"))
                .findFirst()
                .orElseThrow();
        // Input for a REMOVED case must fall back to the baseline item.
        assertThat(removed.input()).isNotNull();
    }

    @Test
    void positionalPairing_whenNotDatasetLinked() {
        Fixture fixture = new Fixture();
        ExperimentRun baseline = fixture.run(null, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(null, RunStatus.SUCCESS, "main");
        for (int i = 0; i < 4; i++) {
            fixture.itemResult(baseline, null, 1.0, true);
            fixture.itemResult(candidate, null, 1.0, true);
        }
        entityManager.flush();
        entityManager.clear();

        DiffView view = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "ALL", PageRequest.of(0, 50));

        assertThat(view.summary().pairing()).isEqualTo("positional");
        assertThat(view.cases().content()).allSatisfy(c -> assertThat(c.index()).startsWith("item-"));
    }

    @Test
    void inputText_populatedFromCandidateItem() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(3);

        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        for (int i = 0; i < version.getItemCount(); i++) {
            DatasetItem item = fixture.item(version, i);
            fixture.itemResult(baseline, item, "base question " + i, 1.0, true);
            fixture.itemResult(candidate, item, "cand question " + i, 1.0, true);
        }
        entityManager.flush();
        entityManager.clear();

        DiffView view = diffService.listDiff(
                fixture.experimentId(), candidate.getId(), baseline.getId(), "ALL", PageRequest.of(0, 50));

        assertThat(view.cases().content()).allSatisfy(c -> assertThat(c.input()).startsWith("cand question "));
    }

    @Test
    void missingBaselineRun_raisesNotFound() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(2);
        ExperimentRun candidate = fixture.run(version, RunStatus.SUCCESS, "main");
        entityManager.flush();
        entityManager.clear();

        UUID experimentId = fixture.experimentId();
        UUID candidateId = candidate.getId();
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> diffService.listDiff(experimentId, candidateId, missing, "ALL", PageRequest.of(0, 50)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Baseline run not found");
    }

    @Test
    void nonTerminalRun_raisesIllegalState() {
        Fixture fixture = new Fixture();
        DatasetVersion version = fixture.datasetVersion(2);
        ExperimentRun baseline = fixture.run(version, RunStatus.SUCCESS, "main");
        ExperimentRun candidate = fixture.run(version, RunStatus.RUNNING, "main");
        entityManager.flush();
        entityManager.clear();

        UUID experimentId = fixture.experimentId();
        UUID candidateId = candidate.getId();
        UUID baselineId = baseline.getId();
        assertThatThrownBy(
                        () -> diffService.listDiff(experimentId, candidateId, baselineId, "ALL", PageRequest.of(0, 50)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal SUCCESS/FAILED");
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
            setField(run, "startedAt", Instant.now().plusSeconds(runOrdinal++));
            run.setGitBranch(branch);
            if (version != null) {
                run.setDatasetVersion(version);
            }
            return entityManager.persist(run);
        }

        void itemResult(ExperimentRun run, DatasetItem datasetItem, double score, boolean success) {
            itemResult(run, datasetItem, "q", score, success);
        }

        void itemResult(ExperimentRun run, DatasetItem datasetItem, String input, double score, boolean success) {
            ItemResult item = new ItemResult(
                    run, Map.of("input", input), Map.of("a", "a"), Map.of("a", success ? "a" : "wrong"), null);
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
